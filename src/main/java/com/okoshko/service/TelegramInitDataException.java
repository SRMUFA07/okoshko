package com.okoshko.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TelegramInitDataException extends RuntimeException {

	public TelegramInitDataException() {
		super("Invalid Telegram Mini App authentication data");
	}

	public TelegramInitDataException(Throwable cause) {
		super("Invalid Telegram Mini App authentication data", cause);
	}
}
