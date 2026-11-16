package com.okoshko.dto;

import java.time.OffsetDateTime;

public record GroupHomeworkResponse(
		long groupId, long homeworkId, String name, String description, OffsetDateTime dueDate) {
}
