package com.okoshko.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record RequestCreatedResponse(
		long requestId,
		String status,
		OffsetDateTime submittedAt,
		LocalDateTime desiredDateTime,
		List<Long> groupIds,
		boolean replayed) {
}
