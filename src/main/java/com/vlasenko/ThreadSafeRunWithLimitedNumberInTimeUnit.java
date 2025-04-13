package com.vlasenko;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ThreadSafeRunWithLimitedNumberInTimeUnit {

    private final Semaphore semaphore;
    private final long intervalMillis;
    private volatile long lastResetTime = System.currentTimeMillis();

    public ThreadSafeRunWithLimitedNumberInTimeUnit(TimeUnit timeUnit, int requestLimit) {

        if (1 > requestLimit) {
            throw new IllegalArgumentException("RequestLimit must be positive");
        }
        this.semaphore = new Semaphore(requestLimit);
        this.intervalMillis = TimeUnit.MILLISECONDS.convert(1, timeUnit);
    }

    /**
     * Метод запуска Runnable процесса с ограничением по количеству в единицу времени.
     *
     * @param command исполняемая задача.
     * @throws InterruptedException Если поток был прерван во время ожидания разрешения на выполнение запроса.
     */
    public void runCommand(Runnable command) throws InterruptedException {

        // Ожидание возможности выполнения запроса
        acquirePermitIfNeeded();

        // Запуск задачи
        command.run();
    }

    /**
     * Метод для получения разрешения на выполнение запроса.
     *
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