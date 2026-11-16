package com.okoshko.repository;

import com.okoshko.dto.SubmitRequestDto;
import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository {
	Optional<Long> findApplicantUserId(long telegramId);
	boolean groupExists(long groupId);
	CreatedRequestEntity create(long userId, SubmitRequestDto request, UUID idempotencyKey,
			OffsetDateTime submittedAt, String consentVersion);
	boolean isAdmin(long telegramId);
	List<AdminRequestEntity> findAllForAdmin();
	DecisionResult processDecision(long requestId, String status);

	enum DecisionResult { UPDATED, NOT_FOUND, NOT_PENDING }
}
