package com.okoshko.service;

import com.okoshko.dto.AdminRequestResponse;
import com.okoshko.dto.RequestCreatedResponse;
import com.okoshko.dto.RequestStatusResponse;
import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import com.okoshko.entity.StudentRequestEntity;
import com.okoshko.repository.RequestRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestService {

	private final RequestRepository requestRepository;
	private final ZoneId schoolTimeZone;

	public RequestService(
			RequestRepository requestRepository,
			@Value("${app.school.time-zone}") String schoolTimeZone) {
		this.requestRepository = requestRepository;
		this.schoolTimeZone = ZoneId.of(schoolTimeZone);
	}

	@Transactional
	public RequestCreatedResponse submit(long telegramId, SubmitRequestDto request, UUID idempotencyKey) {
		validate(request);
		if (idempotencyKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
		}
		long studentId = requireStudentId(telegramId);
		if (!requestRepository.allGroupsExist(request.groupIds())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more groups do not exist");
		}

		LocalDateTime localDateTime = request.desiredDateTime();
		List<ZoneOffset> offsets = schoolTimeZone.getRules().getValidOffsets(localDateTime);
		if (offsets.size() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desired date and time is invalid in the school time zone");
		}
		OffsetDateTime desiredTime = OffsetDateTime.of(localDateTime, offsets.getFirst());

		CreatedRequestEntity created = requestRepository.create(
				studentId, desiredTime, request.groupIds(), idempotencyKey);
		if (!created.replayed() && !desiredTime.toInstant().isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desired date and time must be in the future");
		}
		return new RequestCreatedResponse(
				created.id(),
				created.status(),
				created.submittedAt(),
				created.desiredTime().atZoneSameInstant(schoolTimeZone).toLocalDateTime(),
				created.groupIds(),
				created.replayed());
	}

	public List<RequestCreatedResponse> getStudentRequests(long telegramId) {
		long studentId = requireStudentId(telegramId);
		return requestRepository.findAllForStudent(studentId).stream()
				.map(this::toStudentResponse)
				.toList();
	}

	public List<AdminRequestResponse> getAdminRequests(long telegramId) {
		requireAdmin(telegramId);
		return requestRepository.findAllForAdmin().stream()
				.map(this::toAdminResponse)
				.toList();
	}

	public RequestStatusResponse changeStatus(long telegramId, long requestId, String rawStatus) {
		requireAdmin(telegramId);
		String status = normalizeDecision(rawStatus);
		if (!requestRepository.updatePendingStatus(requestId, status)) {
			if (!requestRepository.exists(requestId)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
			}
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is no longer pending");
		}
		return new RequestStatusResponse(requestId, status);
	}

	private long requireStudentId(long telegramId) {
		return requestRepository.findStudentIdByTelegramId(telegramId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student role is required"));
	}

	private void requireAdmin(long telegramId) {
		if (!requestRepository.isAdmin(telegramId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
		}
	}

	private static void validate(SubmitRequestDto request) {
		if (request == null || request.desiredDateTime() == null
				|| request.groupIds() == null || request.groupIds().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group ids and desired date-time are required");
		}
		if (request.groupIds().stream().anyMatch(id -> id == null || id <= 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group ids must be positive");
		}
		Set<Long> uniqueGroupIds = new HashSet<>(request.groupIds());
		if (uniqueGroupIds.size() != request.groupIds().size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group ids must not contain duplicates");
		}
	}

	private static String normalizeDecision(String rawStatus) {
		if (rawStatus == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		}
		String status = rawStatus.toLowerCase(Locale.ROOT);
		if (!status.equals("approved") && !status.equals("rejected")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be approved or rejected");
		}
		return status;
	}

	private AdminRequestResponse toAdminResponse(AdminRequestEntity request) {
		LocalDateTime desiredLocalTime = request.desiredTime() == null
				? null
				: request.desiredTime().atZoneSameInstant(schoolTimeZone).toLocalDateTime();
		return new AdminRequestResponse(
				request.id(), request.studentId(), request.studentName(), request.submittedAt(),
				request.status(), desiredLocalTime, request.groupIds());
	}

	private RequestCreatedResponse toStudentResponse(StudentRequestEntity request) {
		LocalDateTime desiredLocalTime = request.desiredTime() == null
				? null
				: request.desiredTime().atZoneSameInstant(schoolTimeZone).toLocalDateTime();
		return new RequestCreatedResponse(
				request.id(), request.status(), request.submittedAt(), desiredLocalTime, request.groupIds(), false);
	}

}
