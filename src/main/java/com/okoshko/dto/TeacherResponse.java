package com.okoshko.dto;

import java.util.List;

public record TeacherResponse(
		long id,
		String firstName,
		String lastName,
		String description,
		Integer experienceYears,
		List<GroupResponse> groups) {

	public record GroupResponse(
			long id,
			String name,
			String englishLevel,
		String schedule,
		long studentCount) {
	}
}
