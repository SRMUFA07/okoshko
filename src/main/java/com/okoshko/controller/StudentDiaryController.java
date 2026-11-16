package com.okoshko.controller;

import com.okoshko.dto.StudentDiaryResponse;
import com.okoshko.service.StudentDiaryService;
import com.okoshko.service.TelegramInitDataVerifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/diary")
public class StudentDiaryController {

	private final StudentDiaryService service;
	private final TelegramInitDataVerifier initDataVerifier;

	public StudentDiaryController(StudentDiaryService service, TelegramInitDataVerifier initDataVerifier) {
		this.service = service;
		this.initDataVerifier = initDataVerifier;
	}

	@GetMapping
	public StudentDiaryResponse getDiary(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return service.getDiary(telegramId);
	}
}
