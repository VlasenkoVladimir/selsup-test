package com.vlasenko;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi1 {

    private final Lock requestLock = new ReentrantLock();
    private final AtomicInteger requestCounter = new AtomicInteger();
    private final long requestLimit;
    private final long intervalNanos;
    private volatile Instant lastRequestTime;

    public CrptApi1(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.requestLimit = requestLimit;
        this.intervalNanos = timeUnit.toNanos(1);
        this.lastRequestTime = Instant.now();
    }

    public Response createDocument(Document document, String signature) {
        try {
            requestLock.lock();

            // Проверяем лимит запросов
            if (isRateLimitExceeded()) {
                Thread.sleep(calculateDelay());
            }

            // Выполняем запрос
            return sendRequest(document, signature);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for rate limit", e);
        } finally {
            requestLock.unlock();
        }
    }

    private boolean isRateLimitExceeded() {
        long currentTime = Instant.now().toEpochMilli();
        long elapsedTime = currentTime - lastRequestTime.toEpochMilli();

        if (elapsedTime >= TimeUnit.NANOSECONDS.toMillis(intervalNanos)) {
            resetRateLimit();
            return false;
        }

        return requestCounter.get() >= requestLimit;
    }

    private long calculateDelay() {
        long currentTime = Instant.now().toEpochMilli();
        long elapsedTime = currentTime - lastRequestTime.toEpochMilli();
        long remainingTime = TimeUnit.NANOSECONDS.toMillis(intervalNanos) - elapsedTime;
        return remainingTime;
    }

    private void resetRateLimit() {
        requestCounter.set(0);
        lastRequestTime = Instant.now();
    }

    private Response sendRequest(Document document, String signature) {
        // Здесь логика отправки HTTP-запроса
        // Для простоты возвращаем заглушку
        return new Response(200, "OK");
    }

    // Внутренние классы

    public static class Document {
        // Поля документа
        private String content;

        public Document(String content) {
            this.content = content;
        }
    }

    public static class Response {
        private int statusCode;
        private String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }
}