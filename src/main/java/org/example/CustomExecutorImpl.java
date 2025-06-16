package org.example;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.util.function.Consumer;

public class CustomExecutorImpl implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final List<BlockingQueue<Runnable>> taskQueues = new ArrayList<>();
    private final List<Worker> workers = new ArrayList<>();
    private final ThreadFactory threadFactory;
    private final AtomicInteger threadId = new AtomicInteger(1);
    private final AtomicInteger queueIndex = new AtomicInteger(0);

    private final Object lock = new Object();
    private volatile boolean isShutdown = false;

    private Consumer<Runnable> rejectedHandler;

    public CustomExecutorImpl(int corePoolSize, int maxPoolSize, long keepAliveTime,
                              TimeUnit timeUnit, int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;

        this.threadFactory = r -> {
            String name = "MyPool-worker-" + threadId.getAndIncrement();
            log("[ThreadFactory] Creating new thread: " + name);
            Thread t = new Thread(r, name);
            t.setDaemon(false);
            return t;
        };

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            handleRejected(command);
            return;
        }

        int index = queueIndex.getAndIncrement() % taskQueues.size();
        BlockingQueue<Runnable> queue = taskQueues.get(index);
        boolean offered = queue.offer(command);

        if (offered) {
            log("[Pool] Task accepted into queue #" + index + ": " + command);
        } else {
            synchronized (lock) {
                if (workers.size() < maxPoolSize) {
                    addWorker();
                    taskQueues.get(taskQueues.size() - 1).offer(command);
                    log("[Pool] Queue full, new worker created, task rerouted.");
                } else {
                    handleRejected(command);
                }
            }
        }

        ensureMinSpareThreads();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        execute(future);
        return future;
    }

    private void addWorker() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
        taskQueues.add(queue);
        Worker worker = new Worker(queue);
        Thread t = threadFactory.newThread(worker);
        workers.add(worker);
        t.start();
    }

    private void ensureMinSpareThreads() {
        int idleCount = 0;
        synchronized (lock) {
            for (Worker w : workers) {
                if (w.isIdle()) {
                    idleCount++;
                }
            }
            if (idleCount < minSpareThreads && workers.size() < maxPoolSize) {
                addWorker();
                log("[Pool] Added spare thread to maintain minimum idle threads.");
            }
        }
    }

    private void handleRejected(Runnable command) {
        if (rejectedHandler != null) {
            rejectedHandler.accept(command);
        } else {
            log("[Rejected] Task " + command + " was rejected due to overload!");
        }
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        for (Worker w : workers) {
            w.shutdown();
        }
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        for (Worker w : workers) {
            w.shutdownNow();
        }
    }

    public void setRejectedTaskHandler(Consumer<Runnable> handler) {
        this.rejectedHandler = handler;
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> queue;
        private volatile boolean running = true;
        private volatile boolean idle = true;

        public Worker(BlockingQueue<Runnable> queue) {
            this.queue = queue;
        }

        public boolean isIdle() {
            return idle;
        }

        public void shutdown() {
            running = false;
        }

        public void shutdownNow() {
            running = false;
            Thread.currentThread().interrupt();
        }

        @Override
        public void run() {
            Thread current = Thread.currentThread();
            try {
                while (running || !queue.isEmpty()) {
                    Runnable task = null;
                    try {
                        task = queue.poll(keepAliveTime, timeUnit);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (task != null) {
                        if (isShutdown) break;
                        idle = false;
                        log("[Worker] " + current.getName() + " executes " + task);
                        try {
                            task.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        idle = true;
                    } else {
                        synchronized (lock) {
                            if (workers.size() > corePoolSize) {
                                log("[Worker] " + current.getName() + " idle timeout, stopping.");
                                workers.remove(this);
                                break;
                            }
                        }
                    }
                }
            } finally {
                log("[Worker] " + current.getName() + " terminated.");
            }
        }
    }

    private void log(String msg) {
        //System.out.println(msg);
    }
}
