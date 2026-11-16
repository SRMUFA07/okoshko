package com.okoshko.service;

import com.okoshko.dto.StudentDiaryResponse;
import com.okoshko.repository.StudentDiaryRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentDiaryService {

	private final StudentDiaryRepository repository;

	public StudentDiaryService(StudentDiaryRepository repository) {
		this.repository = repository;
	}

	public StudentDiaryResponse getDiary(long telegramId) {
		long studentId = repository.findStudentIdByTelegramId(telegramId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student role is required"));
		return new StudentDiaryResponse(repository.findGroupsByStudentId(studentId));
	}
}
