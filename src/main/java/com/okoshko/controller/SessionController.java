package com.okoshko.controller;

import com.okoshko.dto.SessionResponse;
import com.okoshko.service.SessionService;
import com.okoshko.service.TelegramInitDataVerifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {

	private final SessionService service;
	private final TelegramInitDataVerifier initDataVerifier;

	public SessionController(SessionService service, TelegramInitDataVerifier initDataVerifier) {
		this.service = service;
		this.initDataVerifier = initDataVerifier;
	}

	@PostMapping
	public SessionResponse open(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return service.open(telegramId);
	}

	@PutMapping("/notifications")
	public void setNotifications(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData,
			@RequestBody NotificationPreferenceRequest request) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		service.setNotificationsEnabled(telegramId, request.enabled());
	}

	public record NotificationPreferenceRequest(Boolean enabled) {
		public NotificationPreferenceRequest {
			if (enabled == null) {
				throw new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.BAD_REQUEST, "enabled is required");
			}
		}
	}
}
