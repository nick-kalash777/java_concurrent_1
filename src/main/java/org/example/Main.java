package org.example;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CustomExecutor executor = new CustomExecutorImpl(
                2, // corePoolSize
                4, // maxPoolSize
                5, TimeUnit.SECONDS, // keepAliveTime
                5, // queueSize
                1  // minSpareThreads
        );

        System.out.println("=== Submitting tasks ===");

        for (int i = 1; i <= 20; i++) {
            final int taskId = i;
            executor.execute(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("[Task] Start task #" + taskId + " on " + threadName);
                try {
                    Thread.sleep(2000); // Simulate work
                } catch (InterruptedException ignored) {}
                System.out.println("[Task] End task #" + taskId + " on " + threadName);
            });
        }

        System.out.println("=== Waiting for tasks to complete ===");

        Thread.sleep(15000); // Ждем, чтобы задачи отработали (время больше, чем у большинства задач)

        System.out.println("=== Shutting down executor ===");
        executor.shutdown();

        // Ждём, чтобы потоки успели завершиться корректно
        Thread.sleep(5000);

        System.out.println("=== Done ===");
    }
}

