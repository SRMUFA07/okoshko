package com.okoshko.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TelegramBotPollingService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotPollingService.class);
    private static final String START_MESSAGE = "Привет! Это бот приложения «Окошко» для учеников и преподавателей онлайн-школы английского. Откройте приложение кнопкой меню, чтобы посмотреть доступные разделы.";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(35);

    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread pollingThread;
    private long nextOffset;

    public TelegramBotPollingService(
            @Value("${telegram.bot.token:}") String botToken,
            ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void start() {
        if (botToken.isBlank()) {
            log.info("Telegram bot polling is disabled because TELEGRAM_BOT_TOKEN is not configured");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        pollingThread = Thread.ofVirtual()
                .name("telegram-bot-polling")
                .start(this::pollUpdates);
    }

    private void pollUpdates() {
        while (running.get()) {
            try {
                JsonNode updates = getUpdates();
                for (JsonNode update : updates) {
                    long updateId = update.path("update_id").asLong();
                    nextOffset = updateId + 1;
                    try {
                        handleUpdate(update);
                    } catch (InterruptedException exception) {
                        throw exception;
                    } catch (IOException | RuntimeException exception) {
                        if (running.get()) {
                            log.warn("Telegram update {} could not be handled: {}",
                                    updateId, safeFailureReason(exception));
                        }
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | RuntimeException exception) {
                if (running.get()) {
                    // Do not log request URIs or exception details: Telegram API URLs contain the bot token.
                    log.warn("Telegram polling failed; retrying in 5 seconds ({})", exception.getClass().getSimpleName());
                    try {
                        Thread.sleep(Duration.ofSeconds(5));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private JsonNode getUpdates() throws IOException, InterruptedException {
        String uri = apiUri("getUpdates") + "?timeout=30&offset=" + nextOffset;
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(POLL_TIMEOUT)
                .GET()
                .build();
        JsonNode response = send(request);
        return response.path("result");
    }

    private void handleUpdate(JsonNode update) throws IOException, InterruptedException {
        JsonNode message = update.path("message");
        JsonNode chatId = message.path("chat").path("id");
        String text = message.path("text").asText("").trim();
        if (chatId.isMissingNode() || text.isEmpty()) {
            return;
        }

        if (text.equals("/start") || text.startsWith("/start ")) {
            sendMessage(chatId, START_MESSAGE);
        }
    }

    private void sendMessage(JsonNode chatId, String text)
            throws IOException, InterruptedException {
        sendJson("sendMessage", Map.of("chat_id", chatId, "text", text));
    }

    private void sendJson(String method, Map<String, Object> payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUri(method)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        send(request);
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body;
        try {
            body = objectMapper.readTree(response.body());
        } catch (RuntimeException exception) {
            throw new IOException("Telegram API returned an unreadable response");
        }
        if (!body.path("ok").asBoolean(false)) {
            int errorCode = body.path("error_code").asInt(response.statusCode());
            String description = body.path("description").asText("Telegram API rejected the request");
            throw new TelegramApiException(errorCode, description);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Telegram API returned HTTP " + response.statusCode());
        }
        return body;
    }

    private String safeFailureReason(Exception exception) {
        if (exception instanceof TelegramApiException telegramException) {
            return "Telegram API " + telegramException.errorCode + ": " + telegramException.description;
        }
        return exception.getClass().getSimpleName();
    }

    private String apiUri(String method) {
        return "https://api.telegram.org/bot" + botToken + "/" + method;
    }

    @Override
    public void stop() {
        running.set(false);
        Thread thread = pollingThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private static class TelegramApiException extends IOException {
        private final int errorCode;
        private final String description;

        private TelegramApiException(int errorCode, String description) {
            super("Telegram API error " + errorCode);
            this.errorCode = errorCode;
            this.description = description;
        }
    }
}
