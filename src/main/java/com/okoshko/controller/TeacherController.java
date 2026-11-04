package com.okoshko.controller;

import com.okoshko.dto.TeacherResponse;
import com.okoshko.service.TeacherService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

	private final TeacherService teacherService;

	public TeacherController(TeacherService teacherService) {
		this.teacherService = teacherService;
	}

	@GetMapping
	public List<TeacherResponse> getTeachers() {
		return teacherService.getTeachers();
	}

	@GetMapping("/{id}")
	public TeacherResponse getTeacher(@PathVariable long id) {
		return teacherService.getTeacher(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found"));
	}
}
