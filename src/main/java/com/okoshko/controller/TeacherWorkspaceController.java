package com.okoshko.controller;

import com.okoshko.dto.TeacherWorkspaceResponse;
import com.okoshko.dto.ChangeGroupLessonLinkDto;
import com.okoshko.dto.GroupLessonLinkResponse;
import com.okoshko.dto.ChangeGroupHomeworkRequest;
import com.okoshko.dto.GroupHomeworkResponse;
import com.okoshko.service.TeacherWorkspaceService;
import com.okoshko.service.TelegramInitDataVerifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherWorkspaceController {

	private final TeacherWorkspaceService service;
	private final TelegramInitDataVerifier initDataVerifier;

	public TeacherWorkspaceController(
			TeacherWorkspaceService service,
			TelegramInitDataVerifier initDataVerifier) {
		this.service = service;
		this.initDataVerifier = initDataVerifier;
	}

	@GetMapping("/workspace")
	public TeacherWorkspaceResponse getWorkspace(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return service.getWorkspace(telegramId);
	}

	@PutMapping("/groups/{groupId}/lesson-link")
	public GroupLessonLinkResponse updateLessonLink(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData,
			@PathVariable long groupId,
			@RequestBody ChangeGroupLessonLinkDto request) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return service.updateLessonLink(telegramId, groupId, request.lessonLink());
	}

	@PutMapping("/groups/{groupId}/homework")
	public GroupHomeworkResponse saveHomework(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData,
			@PathVariable long groupId,
			@RequestBody ChangeGroupHomeworkRequest request) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return service.saveHomework(
				telegramId, groupId, request.name(), request.description(), request.dueDate());
	}
}
