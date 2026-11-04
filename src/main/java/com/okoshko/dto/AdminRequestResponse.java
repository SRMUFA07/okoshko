package com.okoshko.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminRequestResponse(
		long requestId,
		long studentId,
		String studentName,
		OffsetDateTime submittedAt,
		String status,
		LocalDateTime desiredDateTime,
		List<Long> groupIds) {
}
