package com.okoshko.service;

import com.okoshko.dto.AdminRequestResponse;
import com.okoshko.dto.RequestCreatedResponse;
import com.okoshko.dto.RequestStatusResponse;
import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import com.okoshko.repository.RequestRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestService {

	private final RequestRepository requestRepository;

	public RequestService(RequestRepository requestRepository) {
		this.requestRepository = requestRepository;
	}

	@Transactional
	public RequestCreatedResponse submit(long telegramId, SubmitRequestDto request, UUID idempotencyKey) {
		validate(request);
		if (idempotencyKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
		}
		long userId = requestRepository.findApplicantUserId(telegramId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User role is required"));
		if (!requestRepository.groupExists(request.groupId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected group does not exist");
		}
		CreatedRequestEntity created = requestRepository.create(userId, request, idempotencyKey,
				OffsetDateTime.now(), "consent-demo-v1");
		return new RequestCreatedResponse(created.id(), created.status(), created.submittedAt(),
				created.groupId(), created.replayed());
	}

	public List<AdminRequestResponse> getAdminRequests(long telegramId) {
		requireAdmin(telegramId);
		return requestRepository.findAllForAdmin().stream().map(this::toAdminResponse).toList();
	}

	@Transactional
	public RequestStatusResponse changeStatus(long telegramId, long requestId, String rawStatus) {
		requireAdmin(telegramId);
		String status = normalizeDecision(rawStatus);
		RequestRepository.DecisionResult result = requestRepository.processDecision(requestId, status);
		if (result == RequestRepository.DecisionResult.NOT_FOUND) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
		}
		if (result == RequestRepository.DecisionResult.NOT_PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is no longer pending");
		}
		return new RequestStatusResponse(requestId, status);
	}

	private void requireAdmin(long telegramId) {
		if (!requestRepository.isAdmin(telegramId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
		}
	}

	private static void validate(SubmitRequestDto request) {
		if (request == null || request.groupId() == null || request.groupId() <= 0
				|| !Boolean.TRUE.equals(request.personalDataConsent())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group and personal data consent are required");
		}
		validateName(request.childFirstName(), "Child first name");
		validateName(request.childLastName(), "Child last name");
		String phone = request.parentPhone() == null ? "" : request.parentPhone().trim();
		if (phone.length() < 7 || phone.length() > 32 || !phone.matches("[+0-9()\\-\\s]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent phone is invalid");
		}
	}

	private static void validateName(String value, String field) {
		if (value == null || value.trim().isEmpty() || value.trim().length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required and must be at most 100 characters");
		}
	}

	private static String normalizeDecision(String rawStatus) {
		if (rawStatus == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		String status = rawStatus.toLowerCase(Locale.ROOT);
		if (!status.equals("approved") && !status.equals("rejected")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be approved or rejected");
		}
		return status;
	}

	private AdminRequestResponse toAdminResponse(AdminRequestEntity request) {
		return new AdminRequestResponse(request.id(), request.studentId(), request.studentName(), request.parentPhone(),
				request.submittedAt(), request.status(), request.groupName());
	}
}
