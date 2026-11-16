package com.okoshko.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentDiaryResponse(List<GroupResponse> groups) {
	public record GroupResponse(long id, String name, String schedule, String lessonLink,
			List<LessonResponse> lessons, List<HomeworkResponse> homework) {
	}
	public record LessonResponse(long id, LocalDate date, LocalTime time) {
	}
	public record HomeworkResponse(long id, String name, String description, OffsetDateTime dueDate) {
	}
}
