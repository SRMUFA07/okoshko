package com.okoshko.entity;

import java.time.OffsetDateTime;

public record AdminRequestEntity(
		long id,
		Long studentId,
		String studentName,
		String parentPhone,
		OffsetDateTime submittedAt,
		String status,
		String groupName) {
}
