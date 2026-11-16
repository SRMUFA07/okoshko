package com.okoshko.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record TeacherWorkspaceResponse(List<GroupResponse> groups) {

	public record GroupResponse(
			long id,
			String name,
			String englishLevel,
			String schedule,
			String lessonLink,
			long studentCount,
			List<LessonResponse> lessons,
			List<HomeworkResponse> homework) {
	}

	public record LessonResponse(long id, LocalDate date, LocalTime time) {
	}
	public record HomeworkResponse(long id, String name, String description, OffsetDateTime dueDate) {
	}
}
