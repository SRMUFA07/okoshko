package com.okoshko.entity;

import java.time.OffsetDateTime;

public record CreatedRequestEntity(
		long id,
		String status,
		OffsetDateTime submittedAt,
		long groupId,
		boolean replayed) {
}
