package com.vlasenko;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class ThreadSafeApi {

    private final int requestLimit;
    private final long intervalMillis;
    private final Lock rateLimitLock = new ReentrantLock();
    private final AtomicInteger requestsMade = new AtomicInteger();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private Instant lastResetTime = Instant.now();

    public ThreadSafeApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.intervalMillis = timeUnit.toMillis(1);
    }

    public <T> HttpResponse<String> post(String url, T body) throws InterruptedException {
        return sendRequest(() -> {
            String jsonBody = null;
            try {
                jsonBody = objectMapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .build();
//            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        });
    }

    private <T> T sendRequest(Function<Void, T> requestFunction) throws InterruptedException {
        rateLimitLock.lock();
        try {
            checkAndResetRateLimit();
            while (requestsMade.get() >= requestLimit) {
                rateLimitLock.unlock();
                Thread.sleep(10);
                rateLimitLock.lock();
                checkAndResetRateLimit();
            }
            requestsMade.incrementAndGet();
            return requestFunction.apply(null);
        } finally {
            rateLimitLock.unlock();
        }
    }

    private void checkAndResetRateLimit() {
        Instant now = Instant.now();
        if (Duration.between(lastResetTime, now).toMillis() >= intervalMillis) {
            requestsMade.set(0);
            lastResetTime = now;
        }
    }

//    // Вспомогательные классы
//    private static class RequestWrapper<T> {
//        private final String url;
//        private final T body;
//
//        public RequestWrapper(String url, T body) {
//            this.url = url;
//            this.body = body;
//        }
//    }
//
//    // Утилитный класс для сериализации
//    private static class JsonUtils {
//        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
//
//        public static String toJson(Object object) throws JsonProcessingException {
//            return OBJECT_MAPPER.writeValueAsString(object);
//        }
//    }
//
//    // Утилитный класс для HTTP запросов
//    private static class HttpUtils {
//        private static final HttpClient CLIENT = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_2)
//                .connectTimeout(Duration.ofSeconds(30))
//                .build();
//
//        public static HttpResponse<String> sendRequest(HttpRequest request) throws InterruptedException {
//            try {
//                return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw e;
//            } catch (Exception e) {
//                throw new RuntimeException("Failed to send HTTP request", e);
//            }
//        }
//    }
}