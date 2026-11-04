package com.okoshko.controller;

import com.okoshko.dto.AdminRequestResponse;
import com.okoshko.dto.ChangeRequestStatusDto;
import com.okoshko.dto.RequestStatusResponse;
import com.okoshko.service.RequestService;
import com.okoshko.service.TelegramInitDataVerifier;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/requests")
public class AdminRequestController {

	private final RequestService requestService;
	private final TelegramInitDataVerifier initDataVerifier;

	public AdminRequestController(RequestService requestService, TelegramInitDataVerifier initDataVerifier) {
		this.requestService = requestService;
		this.initDataVerifier = initDataVerifier;
	}

	@GetMapping
	public List<AdminRequestResponse> getRequests(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return requestService.getAdminRequests(telegramId);
	}

	@PatchMapping("/{requestId}/status")
	public RequestStatusResponse changeStatus(
			@RequestHeader(name = "X-Telegram-Init-Data", required = false) String initData,
			@PathVariable long requestId,
			@RequestBody ChangeRequestStatusDto status) {
		long telegramId = initDataVerifier.verifyAndGetTelegramUserId(initData);
		return requestService.changeStatus(telegramId, requestId, status.status());
	}
}
