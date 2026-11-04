package com.okoshko.repository;

import com.okoshko.entity.TeacherEntity;
import com.okoshko.entity.TeacherGroupEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTeacherRepository implements TeacherRepository {

	private static final String SELECT_TEACHERS = """
			SELECT t.teacher_id,
			       u.first_name,
			       u.last_name,
			       t.description,
			       t.experience_years,
			       g.group_id,
			       g.name AS group_name,
			       g.english_level AS group_english_level,
			       g.class_schedule AS group_schedule
			  FROM teachers t
			  JOIN users u ON u.user_id = t.user_id
			  LEFT JOIN groups g ON g.teacher_id = t.teacher_id
			 WHERE (CAST(? AS bigint) IS NULL OR t.teacher_id = ?)
			 ORDER BY u.last_name, u.first_name, g.name
			""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcTeacherRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<TeacherEntity> findAll() {
		return queryTeachers(null);
	}

	@Override
	public Optional<TeacherEntity> findById(long id) {
		return queryTeachers(id).stream().findFirst();
	}

	private List<TeacherEntity> queryTeachers(Long teacherId) {
		ResultSetExtractor<List<TeacherEntity>> extractor = this::extractTeachers;
		return jdbcTemplate.query(SELECT_TEACHERS, extractor, teacherId, teacherId);
	}

	private List<TeacherEntity> extractTeachers(ResultSet resultSet) throws SQLException {
		Map<Long, TeacherBuilder> teachers = new LinkedHashMap<>();
		while (resultSet.next()) {
			long id = resultSet.getLong("teacher_id");
			TeacherBuilder teacher = teachers.get(id);
			if (teacher == null) {
				teacher = new TeacherBuilder(
						id,
						resultSet.getString("first_name"),
						resultSet.getString("last_name"),
						resultSet.getString("description"),
						resultSet.getObject("experience_years", Integer.class));
				teachers.put(id, teacher);
			}

			Long groupId = resultSet.getObject("group_id", Long.class);
			if (groupId != null) {
				teacher.groups.add(new TeacherGroupEntity(
						groupId,
						resultSet.getString("group_name"),
						resultSet.getString("group_english_level"),
						resultSet.getString("group_schedule")));
			}
		}

		return teachers.values().stream().map(TeacherBuilder::build).toList();
	}

	private static final class TeacherBuilder {

		private final long id;
		private final String firstName;
		private final String lastName;
		private final String description;
		private final Integer experienceYears;
		private final List<TeacherGroupEntity> groups = new ArrayList<>();

		private TeacherBuilder(long id, String firstName, String lastName, String description, Integer experienceYears) {
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
			this.description = description;
			this.experienceYears = experienceYears;
		}

		private TeacherEntity build() {
			return new TeacherEntity(id, firstName, lastName, description, experienceYears, List.copyOf(groups));
		}
	}
}
