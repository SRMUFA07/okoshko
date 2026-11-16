package com.okoshko.service;

import com.okoshko.dto.TeacherResponse;
import com.okoshko.dto.TeacherResponse.GroupResponse;
import com.okoshko.entity.TeacherEntity;
import com.okoshko.repository.TeacherRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TeacherService {

	private final TeacherRepository teacherRepository;

	public TeacherService(TeacherRepository teacherRepository) {
		this.teacherRepository = teacherRepository;
	}

	public List<TeacherResponse> getTeachers() {
		return teacherRepository.findAll().stream().map(TeacherService::toResponse).toList();
	}

	public Optional<TeacherResponse> getTeacher(long id) {
		return teacherRepository.findById(id).map(TeacherService::toResponse);
	}

	private static TeacherResponse toResponse(TeacherEntity teacher) {
		List<GroupResponse> groups = teacher.groups().stream()
				.map(group -> new GroupResponse(
						group.id(), group.name(), group.englishLevel(), group.schedule(), group.studentCount()))
				.toList();
		return new TeacherResponse(
				teacher.id(),
				teacher.firstName(),
				teacher.lastName(),
				teacher.description(),
				teacher.experienceYears(),
				groups);
	}
}
