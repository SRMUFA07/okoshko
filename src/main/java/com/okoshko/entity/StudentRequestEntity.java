package com.okoshko.entity;

import java.time.OffsetDateTime;
import java.util.List;

public record StudentRequestEntity(
		long id,
		String status,
		OffsetDateTime submittedAt,
		OffsetDateTime desiredTime,
		List<Long> groupIds) {
}
