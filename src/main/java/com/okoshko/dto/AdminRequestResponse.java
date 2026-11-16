package com.okoshko.dto;

import java.time.OffsetDateTime;

public record AdminRequestResponse(
		long requestId,
		Long studentId,
		String studentName,
		String parentPhone,
		OffsetDateTime submittedAt,
		String status,
		String groupName) {
}
