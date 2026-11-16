package com.okoshko.entity;

import java.time.OffsetDateTime;

public record GroupHomeworkEntity(
		long id, long groupId, String name, String description, OffsetDateTime dueDate) {
}
