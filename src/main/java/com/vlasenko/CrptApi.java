package com.vlasenko;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {

    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final TimeUnit timeUnit;
    private final long interval;
    private final AtomicInteger requestCount;
    private final int THREAD_POOL_SIZE = 10;
    private final int SHUTDOWN_WAITING_SECONDS = 60;
    private ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("RequestLimit must be positive");
        }
        this.timeUnit = timeUnit;
        this.interval = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit);
        this.requestCount = new AtomicInteger(0);
        this.scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
        startOrResetTask(requestLimit);
    }

    void startOrResetTask(int requestLimit) {
        scheduler.scheduleAtFixedRate(() -> {
            requestCount.set(0);
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    // todo откуда документ брать?
    public void makeRequest(Document doc, String signature) throws InterruptedException, JsonProcessingException {
        semaphore.acquire();

        String message = objectMapper.writeValueAsString(new MessageDto(doc, signature));

        try {
            // Увеличиваем счетчик запросов
            int currentCount = requestCount.incrementAndGet();
            // Здесь вы можете добавить логику для выполнения запроса к API
            System.out.println("Request made. Current count: " + currentCount);
        } finally {
            semaphore.release();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_WAITING_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    public static class Document {
        String body;

        public Document(String body) {
            this.body = body;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    public class MessageDto{
        Document document;
        String signature;

        public MessageDto(Document document, String signature) {
            this.document = document;
            this.signature = signature;
        }

        public Document getDocument() {
            return document;
        }

        public void setDocument(Document document) {
            this.document = document;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }
}