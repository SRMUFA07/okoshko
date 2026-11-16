package com.okoshko.service;

import com.okoshko.dto.SessionResponse;
import com.okoshko.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

	private final SessionRepository repository;

	public SessionService(SessionRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public SessionResponse open(long telegramId) {
		SessionRepository.SessionRecord record = repository.findOrCreate(telegramId);
		return new SessionResponse(record.roles(), record.notificationsEnabled(), record.telegramIdNoticeAcceptedAt());
	}

	@Transactional
	public void setNotificationsEnabled(long telegramId, boolean enabled) {
		repository.updateNotificationsEnabled(telegramId, enabled);
	}
}
