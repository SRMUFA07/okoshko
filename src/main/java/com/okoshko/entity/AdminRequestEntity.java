package com.okoshko.entity;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminRequestEntity(
		long id,
		long studentId,
		String studentName,
		OffsetDateTime submittedAt,
		String status,
		OffsetDateTime desiredTime,
		List<Long> groupIds) {
}
