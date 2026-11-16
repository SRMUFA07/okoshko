package com.okoshko.repository;

import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class JdbcRequestRepository implements RequestRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcRequestRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	@Override
	public Optional<Long> findApplicantUserId(long telegramId) {
		try {
			return Optional.ofNullable(jdbcTemplate.queryForObject("""
					SELECT u.user_id FROM users u
					JOIN user_roles ur ON ur.user_id = u.user_id
					JOIN roles r ON r.role_id = ur.role_id
					WHERE u.telegram_id = ? AND r.name = 'user'
					""", Long.class, telegramId));
		} catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
	}

	@Override
	public boolean groupExists(long groupId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM groups WHERE group_id = ?)", Boolean.class, groupId));
	}

	@Override
	public CreatedRequestEntity create(long userId, SubmitRequestDto request, UUID key,
			OffsetDateTime submittedAt, String consentVersion) {
		List<CreatedRequestEntity> inserted = jdbcTemplate.query("""
				INSERT INTO requests (applicant_user_id, child_first_name, child_last_name, parent_phone,
					consent_accepted_at, consent_text_version, submitted_at, idempotency_key)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (applicant_user_id, idempotency_key)
					WHERE applicant_user_id IS NOT NULL AND idempotency_key IS NOT NULL DO NOTHING
				RETURNING request_id, status, submitted_at
				""", (rs, row) -> new CreatedRequestEntity(rs.getLong("request_id"), rs.getString("status"),
				rs.getObject("submitted_at", OffsetDateTime.class), request.groupId(), false),
				userId, request.childFirstName().trim(), request.childLastName().trim(), request.parentPhone().trim(),
				submittedAt, consentVersion, submittedAt, key);
		if (!inserted.isEmpty()) {
			jdbcTemplate.update("INSERT INTO request_groups (request_id, group_id) VALUES (?, ?)",
					inserted.getFirst().id(), request.groupId());
			return inserted.getFirst();
		}
		try {
			CreatedRequestEntity existing = jdbcTemplate.queryForObject("""
					SELECT r.request_id, r.status, r.submitted_at, r.child_first_name, r.child_last_name,
					       r.parent_phone, rg.group_id
				FROM requests r JOIN request_groups rg ON rg.request_id = r.request_id
				WHERE r.applicant_user_id = ? AND r.idempotency_key = ?
				""", (rs, row) -> {
					if (!request.childFirstName().trim().equals(rs.getString("child_first_name"))
							|| !request.childLastName().trim().equals(rs.getString("child_last_name"))
							|| !request.parentPhone().trim().equals(rs.getString("parent_phone"))
							|| request.groupId() != rs.getLong("group_id")) {
						throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different request data");
					}
					return new CreatedRequestEntity(rs.getLong("request_id"), rs.getString("status"),
							rs.getObject("submitted_at", OffsetDateTime.class), rs.getLong("group_id"), true);
				}, userId, key);
			return existing;
		} catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Request replay could not be resolved");
		}
	}

	@Override
	public boolean isAdmin(long telegramId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM users u JOIN user_roles ur ON ur.user_id=u.user_id
				JOIN roles r ON r.role_id=ur.role_id WHERE u.telegram_id=? AND r.name='admin')
				""", Boolean.class, telegramId));
	}

	@Override
	public List<AdminRequestEntity> findAllForAdmin() {
		String sql = """
				SELECT r.request_id, r.student_id, r.child_first_name, r.child_last_name,
				       r.parent_phone, COALESCE(r.submitted_at, CURRENT_TIMESTAMP) AS submitted_at,
				       r.status, string_agg(DISTINCT g.name, ', ' ORDER BY g.name) AS group_name,
				       COALESCE(r.applicant_user_id, s.user_id) AS account_user_id,
				       COALESCE(r.child_first_name, u.first_name) AS first_name,
				       COALESCE(r.child_last_name, u.last_name) AS last_name
				FROM requests r
				LEFT JOIN students s ON s.student_id=r.student_id
				LEFT JOIN users u ON u.user_id=COALESCE(r.applicant_user_id,s.user_id)
				LEFT JOIN request_groups rg ON rg.request_id=r.request_id
				LEFT JOIN groups g ON g.group_id=rg.group_id
				GROUP BY r.request_id,s.user_id,u.user_id
				ORDER BY r.submitted_at DESC,r.request_id DESC
				""";
		ResultSetExtractor<List<AdminRequestEntity>> extractor = rs -> {
			List<AdminRequestEntity> result = new ArrayList<>();
			while (rs.next()) {
				long studentIdRaw = rs.getLong("student_id");
				Long studentId = rs.wasNull() ? null : studentIdRaw;
				String first = rs.getString("first_name");
				String last = rs.getString("last_name");
				String childName = (first == null ? "" : first) + (last == null ? "" : " " + last);
				result.add(new AdminRequestEntity(rs.getLong("request_id"), studentId, childName.trim(),
						rs.getString("parent_phone"), rs.getObject("submitted_at", OffsetDateTime.class),
						rs.getString("status"), rs.getString("group_name")));
			}
			return result;
		};
		return jdbcTemplate.query(sql, extractor);
	}

	@Override
	public DecisionResult processDecision(long requestId, String status) {
		Long accountUserId = findAccountUserId(requestId);
		if (accountUserId == null) return DecisionResult.NOT_FOUND;
		jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE", Long.class, accountUserId);
		List<LockedRequest> rows = jdbcTemplate.query("""
				SELECT request_id,status,applicant_user_id,student_id,child_first_name,child_last_name
				FROM requests WHERE request_id=? FOR UPDATE
				""", (rs, row) -> new LockedRequest(rs.getString("status"),
						(Long) rs.getObject("applicant_user_id"), (Long) rs.getObject("student_id"),
						rs.getString("child_first_name"), rs.getString("child_last_name")), requestId);
		if (rows.isEmpty()) return DecisionResult.NOT_FOUND;
		LockedRequest request = rows.getFirst();
		if (!"pending".equals(request.status())) return DecisionResult.NOT_PENDING;
		Long studentId = request.studentId();
		if ("approved".equals(status) && request.applicantUserId() != null) {
			List<Long> existingStudent = jdbcTemplate.query("SELECT student_id FROM students WHERE user_id=? FOR UPDATE",
					(rs, row) -> rs.getLong(1), request.applicantUserId());
			if (!existingStudent.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already has a student profile");
			}
			studentId = jdbcTemplate.queryForObject("""
					INSERT INTO students (user_id,child_first_name,child_last_name) VALUES (?,?,?) RETURNING student_id
					""", Long.class, request.applicantUserId(), request.childFirstName(), request.childLastName());
			jdbcTemplate.update("INSERT INTO student_groups (student_id,group_id) SELECT ?,group_id FROM request_groups WHERE request_id=?",
					studentId, requestId);
			jdbcTemplate.update("DELETE FROM user_roles WHERE user_id=? AND role_id=(SELECT role_id FROM roles WHERE name='user')",
					request.applicantUserId());
			jdbcTemplate.update("INSERT INTO user_roles (user_id,role_id) SELECT ?,role_id FROM roles WHERE name='student'",
					request.applicantUserId());
		}
		int changed = jdbcTemplate.update("UPDATE requests SET status=?,student_id=COALESCE(?,student_id) WHERE request_id=? AND status='pending'",
				status, studentId, requestId);
		if (changed != 1) return DecisionResult.NOT_PENDING;
		if ("approved".equals(status)) {
			jdbcTemplate.update("""
					UPDATE requests SET status='rejected'
					WHERE applicant_user_id=? AND request_id<>? AND status='pending'
					""", accountUserId, requestId);
		}
		return DecisionResult.UPDATED;
	}

	private Long findAccountUserId(long requestId) {
		List<Long> rows = jdbcTemplate.query("""
				SELECT COALESCE(r.applicant_user_id,s.user_id) FROM requests r
				LEFT JOIN students s ON s.student_id=r.student_id WHERE r.request_id=?
				""", (rs, row) -> (Long) rs.getObject(1), requestId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private record LockedRequest(String status, Long applicantUserId, Long studentId, String childFirstName, String childLastName) { }
}
