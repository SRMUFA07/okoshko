package com.okoshko.repository;

import com.okoshko.dto.StudentDiaryResponse.GroupResponse;
import com.okoshko.dto.StudentDiaryResponse.HomeworkResponse;
import com.okoshko.dto.StudentDiaryResponse.LessonResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStudentDiaryRepository implements StudentDiaryRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcStudentDiaryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findStudentIdByTelegramId(long telegramId) {
		try {
			return Optional.ofNullable(jdbcTemplate.queryForObject("""
					SELECT s.student_id
					  FROM users u
				  JOIN students s ON s.user_id = u.user_id
				  JOIN user_roles ur ON ur.user_id = u.user_id
				  JOIN roles r ON r.role_id = ur.role_id
				 WHERE u.telegram_id = ? AND r.name = 'student'
				""", Long.class, telegramId));
		}
		catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	@Override
	public List<GroupResponse> findGroupsByStudentId(long studentId) {
		List<GroupBuilder> groups = jdbcTemplate.query("""
				SELECT g.group_id, g.name, g.class_schedule, g.lesson_link
				  FROM student_groups sg
				  JOIN groups g ON g.group_id = sg.group_id
				 WHERE sg.student_id = ?
				 ORDER BY g.group_id
				""", (rs, row) -> new GroupBuilder(
					rs.getLong("group_id"), rs.getString("name"),
					rs.getString("class_schedule"), rs.getString("lesson_link")), studentId);
		if (groups.isEmpty()) {
			return List.of();
		}
		Map<Long, GroupBuilder> byId = new LinkedHashMap<>();
		groups.forEach(group -> byId.put(group.id, group));
		String placeholders = String.join(",", java.util.Collections.nCopies(groups.size(), "?"));
		Object[] ids = groups.stream().map(group -> group.id).toArray();
		jdbcTemplate.query("""
				SELECT lesson_id, group_id, lesson_date, lesson_time
				  FROM lessons WHERE group_id IN (%s)
				 ORDER BY lesson_date, lesson_time, lesson_id
				""".formatted(placeholders), (RowCallbackHandler) rs -> addLesson(rs, byId), ids);
		jdbcTemplate.query("""
				SELECT homework_id, group_id, assignment_name, assignment_description, due_date
				  FROM homework_assignments WHERE group_id IN (%s)
				 ORDER BY due_date, homework_id
				""".formatted(placeholders), (RowCallbackHandler) rs -> addHomework(rs, byId), ids);
		return groups.stream().map(GroupBuilder::build).toList();
	}

	private static void addLesson(ResultSet rs, Map<Long, GroupBuilder> groups) throws SQLException {
		groups.get(rs.getLong("group_id")).lessons.add(new LessonResponse(
				rs.getLong("lesson_id"), rs.getObject("lesson_date", LocalDate.class),
				rs.getObject("lesson_time", LocalTime.class)));
	}

	private static void addHomework(ResultSet rs, Map<Long, GroupBuilder> groups) throws SQLException {
		groups.get(rs.getLong("group_id")).homework.add(new HomeworkResponse(
				rs.getLong("homework_id"), rs.getString("assignment_name"),
				rs.getString("assignment_description"), rs.getObject("due_date", OffsetDateTime.class)));
	}

	private static final class GroupBuilder {
		private final long id;
		private final String name;
		private final String schedule;
		private final String lessonLink;
		private final List<LessonResponse> lessons = new ArrayList<>();
		private final List<HomeworkResponse> homework = new ArrayList<>();

		private GroupBuilder(long id, String name, String schedule, String lessonLink) {
			this.id = id;
			this.name = name;
			this.schedule = schedule;
			this.lessonLink = lessonLink;
		}

		private GroupResponse build() {
			return new GroupResponse(id, name, schedule, lessonLink,
					List.copyOf(lessons), List.copyOf(homework));
		}
	}
}
