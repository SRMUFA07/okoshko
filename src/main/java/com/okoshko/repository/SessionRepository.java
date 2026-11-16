package com.okoshko.repository;

import java.time.OffsetDateTime;
import java.util.Set;

public interface SessionRepository {

	SessionRecord findOrCreate(long telegramId);

	void updateNotificationsEnabled(long telegramId, boolean enabled);

	record SessionRecord(Set<String> roles, boolean notificationsEnabled, OffsetDateTime telegramIdNoticeAcceptedAt) {
	}
}
