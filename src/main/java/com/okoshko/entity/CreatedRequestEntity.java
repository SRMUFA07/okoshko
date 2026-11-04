package com.okoshko.entity;

import java.time.OffsetDateTime;
import java.util.List;

public record CreatedRequestEntity(
		long id,
		String status,
		OffsetDateTime submittedAt,
		OffsetDateTime desiredTime,
		List<Long> groupIds,
		boolean replayed) {
}
