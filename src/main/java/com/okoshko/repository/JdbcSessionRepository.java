package com.okoshko.repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionRepository implements SessionRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcSessionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public SessionRecord findOrCreate(long telegramId) {
		Long userId = jdbcTemplate.queryForObject("""
				INSERT INTO users (telegram_id, telegram_id_consent_at, telegram_id_consent_version)
				VALUES (?, CURRENT_TIMESTAMP, 'first-use-v1')
				ON CONFLICT (telegram_id) DO UPDATE SET
				    telegram_id = EXCLUDED.telegram_id,
				    telegram_id_consent_at = COALESCE(users.telegram_id_consent_at, EXCLUDED.telegram_id_consent_at),
				    telegram_id_consent_version = COALESCE(users.telegram_id_consent_version, EXCLUDED.telegram_id_consent_version)
				RETURNING user_id
				""", Long.class, telegramId);
		jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", Long.class, userId);
		Integer roleCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM user_roles WHERE user_id = ?", Integer.class, userId);
		if (roleCount != null && roleCount == 0) {
			jdbcTemplate.update("""
					INSERT INTO user_roles (role_id, user_id)
					SELECT role_id, ? FROM roles WHERE name = 'user'
					ON CONFLICT DO NOTHING
					""", userId);
		}

		Set<String> roles = new LinkedHashSet<>(jdbcTemplate.query("""
				SELECT r.name
				  FROM user_roles ur
				  JOIN roles r USING (role_id)
				 WHERE ur.user_id = ?
				 ORDER BY r.name
				""", (rs, row) -> rs.getString("name"), userId));
		return jdbcTemplate.queryForObject("""
				SELECT notifications_enabled, telegram_id_consent_at
				  FROM users WHERE user_id = ?
				""", (rs, row) -> new SessionRecord(
					roles, rs.getBoolean("notifications_enabled"),
					rs.getObject("telegram_id_consent_at", OffsetDateTime.class)), userId);
	}

	@Override
	public void updateNotificationsEnabled(long telegramId, boolean enabled) {
		int updated = jdbcTemplate.update("""
				UPDATE users u
				   SET notifications_enabled = ?
				  FROM user_roles ur
				  JOIN roles r ON r.role_id = ur.role_id
				 WHERE u.telegram_id = ? AND ur.user_id = u.user_id AND r.name = 'student'
				""", enabled, telegramId);
		if (updated != 1) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "Student role is required");
		}
	}
}
