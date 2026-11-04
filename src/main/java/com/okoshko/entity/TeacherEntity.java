package com.okoshko.entity;

import java.util.List;

public record TeacherEntity(
		long id,
		String firstName,
		String lastName,
		String description,
		Integer experienceYears,
		List<TeacherGroupEntity> groups) {
}
