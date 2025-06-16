package org.example;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkMetricsTest {

    private static final int TASK_COUNT = 100000;
    private static final int TASK_DURATION_MS = 10;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Benchmark: ThreadPoolExecutor ===");
        benchmarkStandardExecutor();

        System.out.println("\n=== Benchmark: CustomExecutorImpl ===");
        benchmarkCustomExecutor();
    }

    private static void benchmarkStandardExecutor() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger callerThreadExecutions = new AtomicInteger();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4,
                5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5),
                r -> new Thread(r, "StdWorker-" + r.hashCode()),
                (r, exec) -> {
                    rejected.incrementAndGet();
                    callerThreadExecutions.incrementAndGet();
                    r.run();
                }
        );

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        long start = System.nanoTime();

        for (int i = 0; i < TASK_COUNT; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(TASK_DURATION_MS);
                } catch (InterruptedException ignored) {}
                completed.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await();
        long end = System.nanoTime();
        executor.shutdown();

        printMetrics("ThreadPoolExecutor", start, end, completed, rejected, callerThreadExecutions);
    }

    private static void benchmarkCustomExecutor() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger callerThreadExecutions = new AtomicInteger();

        CustomExecutorImpl executor = new CustomExecutorImpl(
                2, 4,
                5, TimeUnit.SECONDS,
                5, 1
        );

        executor.setRejectedTaskHandler(task -> {
            rejected.incrementAndGet();
            callerThreadExecutions.incrementAndGet();
            task.run();
        });

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        long start = System.nanoTime();

        for (int i = 0; i < TASK_COUNT; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(TASK_DURATION_MS);
                } catch (InterruptedException ignored) {}
                completed.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await();
        long end = System.nanoTime();
        executor.shutdown();

        printMetrics("CustomExecutorImpl", start, end, completed, rejected, callerThreadExecutions);
    }

    private static void printMetrics(
            String label, long startNs, long endNs,
            AtomicInteger completed, AtomicInteger rejected,
            AtomicInteger inCallerThread
    ) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endNs - startNs);
        double throughput = (completed.get() * 1000.0) / durationMs;

        System.out.println("[" + label + "] Metrics:");
        System.out.println("  ✅ Completed Tasks: " + completed.get());
        System.out.println("  ❌ Rejected Tasks: " + rejected.get());
        System.out.println("  🧍 Executed in Caller Thread: " + inCallerThread.get());
        System.out.printf ("  ⚡ Throughput: %.2f tasks/sec%n", throughput);
        System.out.println("  ⏳ Total time: " + durationMs + " ms");
    }
}
