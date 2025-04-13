package com.vlasenko;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.net.URI.create;

public class ThreadSafeApi1 {

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final long intervalMillis;
    private volatile long lastResetTime = System.currentTimeMillis();

    /**
     * Конструктор класса.
     * @param timeUnit Единица измерения интервала времени.
     * @param requestLimit Максимальное количество запросов в указанный интервал времени.
     */
    public ThreadSafeApi1(TimeUnit timeUnit, int requestLimit) {
        if (1 > requestLimit) {
            throw new IllegalArgumentException("RequestLimit must be positive");
        }
        this.semaphore = new Semaphore(requestLimit);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();
        this.intervalMillis = TimeUnit.MILLISECONDS.convert(1, timeUnit);
    }

    /**
     * Метод для выполнения POST запроса.
     * @param url Адрес ресурса.
     * @param entity Объект для передачи в теле запроса.
     * @return Ответ от сервера.
     * @throws IOException Если произошла ошибка при выполнении запроса.
     * @throws InterruptedException Если поток был прерван во время ожидания разрешения на выполнение запроса.
     */
    public String post(String url, Object entity) throws IOException, InterruptedException {
        // Ожидание возможности выполнения запроса
        acquirePermitIfNeeded();

        // Преобразование объекта в JSON строку
        String jsonBody = objectMapper.writeValueAsString(entity);

        // Создание HTTP запроса
        HttpRequest request = HttpRequest.newBuilder()
                .uri(create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Выполнение запроса
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    /**
     * Метод для получения разрешения на выполнение запроса.
     * @throws InterruptedException Если поток был прерван во время ожидания разрешения.
     */
    private void acquirePermitIfNeeded() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime >= intervalMillis) {
            resetSemaphore();
        } else {
            semaphore.acquire();
        }
    }

    /**
     * Метод для сброса счетчика разрешений и обновления времени последнего сброса.
     */
    private synchronized void resetSemaphore() {
        semaphore.drainPermits();  // Сбрасываем все текущие разрешения
        for (int i = 0; i < semaphore.getQueueLength(); i++) {
            semaphore.release();   // Возвращаем разрешения обратно
        }
        lastResetTime = System.currentTimeMillis();  // Обновляем время последнего сброса
    }
}