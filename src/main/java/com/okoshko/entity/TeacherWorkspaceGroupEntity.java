package com.okoshko.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record TeacherWorkspaceGroupEntity(
		long id,
		String name,
		String englishLevel,
		String schedule,
		String lessonLink,
		long studentCount,
		List<Lesson> lessons,
		List<Homework> homework) {

	public record Lesson(long id, LocalDate date, LocalTime time) {
	}
	public record Homework(long id, String name, String description, OffsetDateTime dueDate) {
	}
}
