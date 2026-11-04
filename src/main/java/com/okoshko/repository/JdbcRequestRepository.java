package com.okoshko.repository;

import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import com.okoshko.entity.StudentRequestEntity;
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

@Repository
public class JdbcRequestRepository implements RequestRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcRequestRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findStudentIdByTelegramId(long telegramId) {
		String sql = """
				SELECT s.student_id
				  FROM users u
			  JOIN students s ON s.user_id = u.user_id
			  JOIN user_roles ur ON ur.user_id = u.user_id
			  JOIN roles r ON r.role_id = ur.role_id
			 WHERE u.telegram_id = ?
			   AND r.name = 'student'
				""";
		try {
			return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class, telegramId));
		}
		catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	@Override
	public boolean allGroupsExist(List<Long> groupIds) {
		String placeholders = String.join(",", java.util.Collections.nCopies(groupIds.size(), "?"));
		String sql = "SELECT COUNT(*) FROM groups WHERE group_id IN (" + placeholders + ")";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, groupIds.toArray());
		return count != null && count == groupIds.size();
	}

	@Override
	public CreatedRequestEntity create(
			long studentId, OffsetDateTime desiredTime, List<Long> groupIds, UUID idempotencyKey) {
		String insertRequest = """
				INSERT INTO requests (student_id, desired_time, idempotency_key)
				VALUES (?, ?, ?)
				ON CONFLICT (student_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
				RETURNING request_id, status, submitted_at
				""";
		List<CreatedRequestEntity> inserted = jdbcTemplate.query(
				insertRequest,
				(resultSet, rowNumber) -> new CreatedRequestEntity(
						resultSet.getLong("request_id"),
						resultSet.getString("status"),
						resultSet.getObject("submitted_at", OffsetDateTime.class),
						desiredTime,
						List.copyOf(groupIds),
						false),
				studentId,
				desiredTime,
				idempotencyKey);
		if (inserted.isEmpty()) {
			return findIdempotentRequest(studentId, desiredTime, groupIds, idempotencyKey);
		}
		CreatedRequestEntity request = inserted.getFirst();

		List<Object[]> links = groupIds.stream()
				.map(groupId -> new Object[] {request.id(), groupId})
				.toList();
		jdbcTemplate.batchUpdate("INSERT INTO request_groups (request_id, group_id) VALUES (?, ?)", links);
		return request;
	}

	private CreatedRequestEntity findIdempotentRequest(
			long studentId, OffsetDateTime desiredTime, List<Long> groupIds, UUID idempotencyKey) {
		String sql = """
				SELECT request_id, status, submitted_at, desired_time
				  FROM requests
			 WHERE student_id = ? AND idempotency_key = ?
				""";
		CreatedRequestEntity row = jdbcTemplate.queryForObject(
				sql,
				(resultSet, rowNumber) -> new CreatedRequestEntity(
						resultSet.getLong("request_id"),
						resultSet.getString("status"),
						resultSet.getObject("submitted_at", OffsetDateTime.class),
						resultSet.getObject("desired_time", OffsetDateTime.class),
						List.of(),
						true),
				studentId,
				idempotencyKey);
		List<Long> existingGroupIds = findGroupIds(row.id());
		CreatedRequestEntity existing = new CreatedRequestEntity(
				row.id(), row.status(), row.submittedAt(), row.desiredTime(), existingGroupIds, true);
		if (!existing.desiredTime().toInstant().equals(desiredTime.toInstant())
				|| !new java.util.HashSet<>(existing.groupIds()).equals(new java.util.HashSet<>(groupIds))) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.CONFLICT,
					"Idempotency key was already used with different request data");
		}
		return existing;
	}

	private List<Long> findGroupIds(long requestId) {
		return jdbcTemplate.query(
				"SELECT group_id FROM request_groups WHERE request_id = ? ORDER BY group_id",
				(resultSet, rowNumber) -> resultSet.getLong("group_id"),
				requestId);
	}

	@Override
	public List<StudentRequestEntity> findAllForStudent(long studentId) {
		String sql = """
				SELECT r.request_id, r.status, r.submitted_at, r.desired_time, rg.group_id
				  FROM requests r
			  LEFT JOIN request_groups rg ON rg.request_id = r.request_id
			 WHERE r.student_id = ?
			 ORDER BY r.submitted_at DESC, r.request_id DESC, rg.group_id
				""";
		ResultSetExtractor<List<StudentRequestEntity>> extractor = this::extractStudentRequests;
		return jdbcTemplate.query(sql, extractor, studentId);
	}

	private List<StudentRequestEntity> extractStudentRequests(ResultSet resultSet) throws SQLException {
		Map<Long, StudentRequestBuilder> requests = new LinkedHashMap<>();
		while (resultSet.next()) {
			long id = resultSet.getLong("request_id");
			StudentRequestBuilder request = requests.get(id);
			if (request == null) {
				request = new StudentRequestBuilder(
						id,
						resultSet.getString("status"),
						resultSet.getObject("submitted_at", OffsetDateTime.class),
						resultSet.getObject("desired_time", OffsetDateTime.class));
				requests.put(id, request);
			}
			Long groupId = resultSet.getObject("group_id", Long.class);
			if (groupId != null) {
				request.groupIds.add(groupId);
			}
		}
		return requests.values().stream().map(StudentRequestBuilder::build).toList();
	}

	@Override
	public boolean isAdmin(long telegramId) {
		Boolean admin = jdbcTemplate.queryForObject("""
				SELECT EXISTS (
				    SELECT 1
				      FROM users u
				      JOIN user_roles ur ON ur.user_id = u.user_id
				      JOIN roles r ON r.role_id = ur.role_id
				     WHERE u.telegram_id = ?
				       AND r.name = 'admin'
				)
				""", Boolean.class, telegramId);
		return Boolean.TRUE.equals(admin);
	}

	@Override
	public List<AdminRequestEntity> findAllForAdmin() {
		String sql = """
				SELECT r.request_id,
				       r.student_id,
				       u.first_name,
				       u.last_name,
				       r.submitted_at,
				       r.status,
				       r.desired_time,
				       rg.group_id
				  FROM requests r
				  JOIN students s ON s.student_id = r.student_id
				  JOIN users u ON u.user_id = s.user_id
				  LEFT JOIN request_groups rg ON rg.request_id = r.request_id
				 ORDER BY r.submitted_at DESC, r.request_id DESC, rg.group_id
				""";
		ResultSetExtractor<List<AdminRequestEntity>> extractor = this::extractAdminRequests;
		return jdbcTemplate.query(sql, extractor);
	}

	private List<AdminRequestEntity> extractAdminRequests(ResultSet resultSet) throws SQLException {
		Map<Long, RequestBuilder> requests = new LinkedHashMap<>();
		while (resultSet.next()) {
			long id = resultSet.getLong("request_id");
			RequestBuilder request = requests.get(id);
			if (request == null) {
				request = new RequestBuilder(
						id,
						resultSet.getLong("student_id"),
						resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
						resultSet.getObject("submitted_at", OffsetDateTime.class),
						resultSet.getString("status"),
						resultSet.getObject("desired_time", OffsetDateTime.class));
				requests.put(id, request);
			}
			Long groupId = resultSet.getObject("group_id", Long.class);
			if (groupId != null) {
				request.groupIds.add(groupId);
			}
		}
		return requests.values().stream().map(RequestBuilder::build).toList();
	}

	@Override
	public boolean updatePendingStatus(long requestId, String status) {
		return jdbcTemplate.update(
				"UPDATE requests SET status = ? WHERE request_id = ? AND status = 'pending'",
				status,
				requestId) == 1;
	}

	@Override
	public boolean exists(long requestId) {
		Boolean exists = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM requests WHERE request_id = ?)", Boolean.class, requestId);
		return Boolean.TRUE.equals(exists);
	}

	private static final class RequestBuilder {

		private final long id;
		private final long studentId;
		private final String studentName;
		private final OffsetDateTime submittedAt;
		private final String status;
		private final OffsetDateTime desiredTime;
		private final List<Long> groupIds = new ArrayList<>();

		private RequestBuilder(
				long id,
				long studentId,
				String studentName,
				OffsetDateTime submittedAt,
				String status,
				OffsetDateTime desiredTime) {
			this.id = id;
			this.studentId = studentId;
			this.studentName = studentName;
			this.submittedAt = submittedAt;
			this.status = status;
			this.desiredTime = desiredTime;
		}

		private AdminRequestEntity build() {
			return new AdminRequestEntity(
					id, studentId, studentName, submittedAt, status, desiredTime, List.copyOf(groupIds));
		}
	}

	private static final class StudentRequestBuilder {

		private final long id;
		private final String status;
		private final OffsetDateTime submittedAt;
		private final OffsetDateTime desiredTime;
		private final List<Long> groupIds = new ArrayList<>();

		private StudentRequestBuilder(long id, String status, OffsetDateTime submittedAt, OffsetDateTime desiredTime) {
			this.id = id;
			this.status = status;
			this.submittedAt = submittedAt;
			this.desiredTime = desiredTime;
		}

		private StudentRequestEntity build() {
			return new StudentRequestEntity(id, status, submittedAt, desiredTime, List.copyOf(groupIds));
		}
	}
}
