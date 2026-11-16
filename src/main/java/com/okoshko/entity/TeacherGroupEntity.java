package com.okoshko.entity;

public record TeacherGroupEntity(
		long id,
		String name,
		String englishLevel,
		String schedule,
		long studentCount) {
}
