package com.okoshko.dto;

import java.time.OffsetDateTime;
import java.util.Set;

public record SessionResponse(Set<String> roles, boolean notificationsEnabled, OffsetDateTime telegramIdNoticeAcceptedAt,
		String firstName, String lastName, String username, String photoUrl) {
}
