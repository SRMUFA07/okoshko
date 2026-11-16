package com.okoshko.repository;

import com.okoshko.entity.TeacherWorkspaceGroupEntity;
import com.okoshko.entity.TeacherWorkspaceGroupEntity.Homework;
import com.okoshko.entity.TeacherWorkspaceGroupEntity.Lesson;
import com.okoshko.entity.GroupHomeworkEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTeacherWorkspaceRepository implements TeacherWorkspaceRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcTeacherWorkspaceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findTeacherIdByTelegramId(long telegramId) {
		try {
			return Optional.ofNullable(jdbcTemplate.queryForObject("""
					SELECT t.teacher_id
					  FROM users u
				  JOIN teachers t ON t.user_id = u.user_id
				  JOIN user_roles ur ON ur.user_id = u.user_id
				  JOIN roles r ON r.role_id = ur.role_id
				 WHERE u.telegram_id = ? AND r.name = 'teacher'
				""", Long.class, telegramId));
		}
		catch (EmptyResultDataAccessException exception) {
			return Optional.empty();
		}
	}

	@Override
	public List<TeacherWorkspaceGroupEntity> findGroupsByTeacherId(long teacherId) {
		List<GroupBuilder> groups = jdbcTemplate.query("""
				SELECT g.group_id, g.name, g.english_level, g.class_schedule, g.lesson_link,
				       (SELECT COUNT(*) FROM student_groups sg WHERE sg.group_id = g.group_id) AS student_count
				  FROM groups g
				 WHERE g.teacher_id = ?
				 ORDER BY g.group_id
				""", (rs, row) -> new GroupBuilder(
					rs.getLong("group_id"), rs.getString("name"), rs.getString("english_level"),
					rs.getString("class_schedule"), rs.getString("lesson_link"), rs.getLong("student_count")),
				teacherId);
		if (groups.isEmpty()) {
			return List.of();
		}

		Map<Long, GroupBuilder> byId = new LinkedHashMap<>();
		groups.forEach(group -> byId.put(group.id, group));
		String placeholders = String.join(",", java.util.Collections.nCopies(groups.size(), "?"));
		Object[] groupIds = groups.stream().map(group -> group.id).toArray();

		jdbcTemplate.query("""
				SELECT lesson_id, group_id, lesson_date, lesson_time
				  FROM lessons
				 WHERE group_id IN (%s)
				 ORDER BY lesson_date, lesson_time, lesson_id
				""".formatted(placeholders), (RowCallbackHandler) rs -> addLesson(rs, byId), groupIds);
		jdbcTemplate.query("""
				SELECT homework_id, group_id, assignment_name, assignment_description, due_date
				  FROM homework_assignments
				 WHERE group_id IN (%s)
				 ORDER BY due_date, homework_id
				""".formatted(placeholders), (RowCallbackHandler) rs -> addHomework(rs, byId), groupIds);

		return groups.stream().map(GroupBuilder::build).toList();
	}

	@Override
	public boolean updateGroupLessonLink(long teacherId, long groupId, String lessonLink) {
		return jdbcTemplate.update(
				"UPDATE groups SET lesson_link = ? WHERE group_id = ? AND teacher_id = ?",
				lessonLink,
				groupId,
				teacherId) == 1;
	}

	@Override
	public Optional<GroupHomeworkEntity> upsertGroupHomework(
			long teacherId, long groupId, String name, String description,
			java.time.OffsetDateTime dueDate) {
		List<GroupHomeworkEntity> rows = jdbcTemplate.query("""
				INSERT INTO homework_assignments (group_id, assignment_name, assignment_description, due_date)
				SELECT ?, ?, ?, ?
				 WHERE EXISTS (SELECT 1 FROM groups WHERE group_id = ? AND teacher_id = ?)
				ON CONFLICT DO UPDATE
				   SET assignment_name = EXCLUDED.assignment_name,
				       assignment_description = EXCLUDED.assignment_description,
				       due_date = EXCLUDED.due_date
				RETURNING homework_id, group_id, assignment_name, assignment_description, due_date
				""",
				(rs, row) -> new GroupHomeworkEntity(
					rs.getLong("homework_id"), rs.getLong("group_id"),
					rs.getString("assignment_name"), rs.getString("assignment_description"),
					rs.getObject("due_date", java.time.OffsetDateTime.class)),
				groupId, name, description, dueDate, groupId, teacherId);
		return rows.stream().findFirst();
	}

	private static void addLesson(ResultSet rs, Map<Long, GroupBuilder> groups) throws SQLException {
		groups.get(rs.getLong("group_id")).lessons.add(new Lesson(
				rs.getLong("lesson_id"), rs.getObject("lesson_date", java.time.LocalDate.class),
				rs.getObject("lesson_time", java.time.LocalTime.class)));
	}

	private static void addHomework(ResultSet rs, Map<Long, GroupBuilder> groups) throws SQLException {
		groups.get(rs.getLong("group_id")).homework.add(new Homework(
				rs.getLong("homework_id"), rs.getString("assignment_name"),
				rs.getString("assignment_description"), rs.getObject("due_date", java.time.OffsetDateTime.class)));
	}

	private static final class GroupBuilder {
		private final long id;
		private final String name;
		private final String englishLevel;
		private final String schedule;
		private final String lessonLink;
		private final long studentCount;
		private final List<Lesson> lessons = new ArrayList<>();
		private final List<Homework> homework = new ArrayList<>();

		private GroupBuilder(long id, String name, String englishLevel, String schedule,
				String lessonLink, long studentCount) {
			this.id = id;
			this.name = name;
			this.englishLevel = englishLevel;
			this.schedule = schedule;
			this.lessonLink = lessonLink;
			this.studentCount = studentCount;
		}

		private TeacherWorkspaceGroupEntity build() {
			return new TeacherWorkspaceGroupEntity(id, name, englishLevel, schedule, lessonLink,
					studentCount, List.copyOf(lessons), List.copyOf(homework));
		}
	}
}
