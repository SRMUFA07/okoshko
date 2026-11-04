package com.okoshko.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TelegramInitDataVerifier {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final byte[] WEB_APP_DATA = "WebAppData".getBytes(StandardCharsets.UTF_8);

	private final String botToken;
	private final Duration maxAge;
	private final JsonMapper jsonMapper;

	public TelegramInitDataVerifier(
			@Value("${telegram.bot.token:}") String botToken,
			@Value("${telegram.init-data.max-age:PT1H}") Duration maxAge,
			JsonMapper jsonMapper) {
		this.botToken = botToken;
		this.maxAge = maxAge;
		this.jsonMapper = jsonMapper;
	}

	public long verifyAndGetTelegramUserId(String rawInitData) {
		if (botToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram authentication is not configured");
		}
		if (rawInitData == null || rawInitData.isBlank() || maxAge.isNegative() || maxAge.isZero()) {
			throw new TelegramInitDataException();
		}

		Map<String, String> parameters = parse(rawInitData);
		String suppliedHash = parameters.remove("hash");
		if (suppliedHash == null || !suppliedHash.matches("(?i)[0-9a-f]{64}")) {
			throw new TelegramInitDataException();
		}

		String dataCheckString = parameters.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(java.util.stream.Collectors.joining("\n"));
		byte[] suppliedHashBytes = HexFormat.of().parseHex(suppliedHash);
		byte[] expectedHash = hmac(hmac(WEB_APP_DATA, botToken.getBytes(StandardCharsets.UTF_8)),
				dataCheckString.getBytes(StandardCharsets.UTF_8));
		if (!MessageDigest.isEqual(expectedHash, suppliedHashBytes)) {
			throw new TelegramInitDataException();
		}

		validateAuthDate(parameters.get("auth_date"));
		return parseTelegramUserId(parameters.get("user"));
	}

	private static Map<String, String> parse(String rawInitData) {
		Map<String, String> parameters = new TreeMap<>();
		try {
			for (String pair : rawInitData.split("&", -1)) {
				int separator = pair.indexOf('=');
				if (separator <= 0) {
					throw new TelegramInitDataException();
				}
				String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
				String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
				if (parameters.putIfAbsent(key, value) != null) {
					throw new TelegramInitDataException();
				}
			}
		}
		catch (IllegalArgumentException exception) {
			throw new TelegramInitDataException(exception);
		}
		return parameters;
	}

	private void validateAuthDate(String rawAuthDate) {
		try {
			Instant authDate = Instant.ofEpochSecond(Long.parseLong(rawAuthDate));
			Instant now = Instant.now();
			if (authDate.isAfter(now) || authDate.isBefore(now.minus(maxAge))) {
				throw new TelegramInitDataException();
			}
		}
		catch (NullPointerException | NumberFormatException | java.time.DateTimeException exception) {
			throw new TelegramInitDataException(exception);
		}
	}

	private long parseTelegramUserId(String rawUser) {
		try {
			JsonNode user = jsonMapper.readTree(rawUser);
			JsonNode id = user == null ? null : user.get("id");
			if (id == null || !id.canConvertToLong() || id.longValue() <= 0) {
				throw new TelegramInitDataException();
			}
			return id.longValue();
		}
		catch (RuntimeException exception) {
			if (exception instanceof TelegramInitDataException telegramException) {
				throw telegramException;
			}
			throw new TelegramInitDataException(exception);
		}
	}

	private static byte[] hmac(byte[] key, byte[] value) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(key, HMAC_SHA256));
			return mac.doFinal(value);
		}
		catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
		}
	}
}
