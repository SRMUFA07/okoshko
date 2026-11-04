package com.okoshko.controller;

import com.okoshko.dto.RequestCreatedResponse;
import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.service.RequestService;
import com.okoshko.service.TelegramInitDataVerifier;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

	private final RequestService requestService;
	private final TelegramInitDataVerifier initDataVerifier;

	public RequestController(RequestService requestService, TelegramInitDataVerifier initDataVerifier) {
		this.requestService = requestService;
		this.initDataVerifier = initDataVerifier;
	}

	@PostMapping
	public ResponseEntity<RequestCreatedResponse> submit(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData,
			@RequestHeader(name = "Idempotency-Key", required = false) UUID idempotencyKey,
			@RequestBody SubmitRequestDto request) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		RequestCreatedResponse response = requestService.submit(telegramId, request, idempotencyKey);
		return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public List<RequestCreatedResponse> getMine(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return requestService.getStudentRequests(telegramId);
	}
}
