package com.okoshko.dto;

import java.time.OffsetDateTime;

public record RequestCreatedResponse(
		long requestId,
		String status,
		OffsetDateTime submittedAt,
		long groupId,
		boolean replayed) {
}
