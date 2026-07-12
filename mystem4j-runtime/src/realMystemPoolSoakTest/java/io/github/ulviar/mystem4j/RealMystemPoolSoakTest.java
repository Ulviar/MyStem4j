package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.sun.management.UnixOperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RealMystemPoolSoakTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PROCESS_RELEASE_TIMEOUT = Duration.ofSeconds(10);
    private static final int[] CONCURRENCY_FACTORS = {2, 4, 8};

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void sustainsConcurrentLoadRotatesWorkersAndReleasesResources() throws Exception {
        Path executable = Path.of(requiredProperty("mystem4j.executable")).toRealPath();
        int requestsPerConcurrency = positiveIntProperty("mystem4j.poolSoakRequests", 10_000);
        int poolSize = positiveIntProperty("mystem4j.poolSoakPoolSize", 4);
        int maxRequestsPerWorker = positiveIntProperty("mystem4j.poolSoakMaxRequestsPerWorker", 250);
        long descriptorsBefore = openFileDescriptorCount();
        List<SoakMetrics> metrics = new ArrayList<>();
        int distinctWorkerPids;
        int maximumAliveWorkers;

        try (ProcessSampler sampler = new ProcessSampler(executable)) {
            sampler.start();
            try (MystemClient client = Mystem.builder()
                    .executable(executable)
                    .requestTimeout(REQUEST_TIMEOUT)
                    .options(MystemOptions.builder()
                            .format(MystemOutputFormat.JSON)
                            .copyInput(true)
                            .build())
                    .pooled(pool -> pool.maxSize(poolSize)
                            .warmupSize(poolSize)
                            .minIdle(poolSize)
                            .acquireTimeout(REQUEST_TIMEOUT)
                            .hookTimeout(REQUEST_TIMEOUT)
                            .maxRequestsPerWorker(maxRequestsPerWorker)
                            .backgroundReplenishment(true))
                    .build()) {
                int requestOffset = 0;
                for (int factor : CONCURRENCY_FACTORS) {
                    int concurrency = poolSize * factor;
                    SoakMetrics result = runLoad(client, concurrency, requestsPerConcurrency, requestOffset);
                    metrics.add(result);
                    requestOffset += requestsPerConcurrency;
                    System.out.printf(
                            "poolSoak concurrency=%d requests=%d throughput=%.1f req/s p95=%.3f ms p99=%.3f ms%n",
                            result.concurrency(),
                            result.requests(),
                            result.throughputPerSecond(),
                            nanosToMillis(result.p95Nanos()),
                            nanosToMillis(result.p99Nanos()));
                }
            }

            awaitNoMatchingProcesses(executable, PROCESS_RELEASE_TIMEOUT);
            sampler.sampleNow();
            assertEquals(0, sampler.currentAlive(), "MyStem workers remain alive after pool close");
            assertTrue(
                    sampler.distinctPids() > poolSize,
                    () -> "worker rotation was not observed: distinct pids=" + sampler.distinctPids());
            assertTrue(
                    sampler.maximumAlive() <= poolSize * 2,
                    () -> "pool process count was not bounded: max alive=" + sampler.maximumAlive());
            distinctWorkerPids = sampler.distinctPids();
            maximumAliveWorkers = sampler.maximumAlive();
        }

        assertEquals(CONCURRENCY_FACTORS.length, metrics.size());
        assertTrue(metrics.stream().allMatch(value -> value.p99Nanos() < REQUEST_TIMEOUT.toNanos()));
        long descriptorsAfter = openFileDescriptorCount();
        System.out.printf(
                "poolSoak resources distinctWorkerPids=%d maxAliveWorkers=%d fdBefore=%d fdAfter=%d%n",
                distinctWorkerPids, maximumAliveWorkers, descriptorsBefore, descriptorsAfter);
        if (descriptorsBefore >= 0 && descriptorsAfter >= 0) {
            assertTrue(
                    descriptorsAfter <= descriptorsBefore + 8,
                    () -> "open file descriptors grew after pool close: before="
                            + descriptorsBefore
                            + ", after="
                            + descriptorsAfter);
        }
    }

    private static SoakMetrics runLoad(
            MystemClient client, int concurrency, int requestCount, int requestOffset) throws Exception {
        long[] latencies = new long[requestCount];
        AtomicInteger nextRequest = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<?>> workers = new ArrayList<>(concurrency);
            for (int worker = 0; worker < concurrency; worker++) {
                workers.add(executor.submit(() -> {
                    start.await();
                    int index;
                    while ((index = nextRequest.getAndIncrement()) < requestCount) {
                        int requestId = requestOffset + index;
                        long requestStarted = System.nanoTime();
                        MystemRawResult result = client.analyze("мама " + requestId);
                        latencies[index] = System.nanoTime() - requestStarted;
                        String expectedMarker = "\"text\":\"" + requestId + "\"";
                        if (!result.output().contains(expectedMarker)) {
                            throw new AssertionError(
                                    "response does not belong to request " + requestId + ": " + result.output());
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> worker : workers) {
                awaitWorker(worker);
            }
        }
        long elapsed = System.nanoTime() - started;
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        return new SoakMetrics(
                concurrency,
                requestCount,
                requestCount / (elapsed / 1_000_000_000.0),
                percentile(sorted, 95),
                percentile(sorted, 99));
    }

    private static void awaitWorker(Future<?> worker) throws Exception {
        try {
            worker.get(12, TimeUnit.MINUTES);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error failure) {
                throw failure;
            }
            throw new AssertionError("pool soak worker failed", cause);
        } catch (TimeoutException error) {
            fail("pool soak worker did not finish", error);
        }
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.length * (percentile / 100.0)) - 1);
        return sorted[index];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "").strip();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing required system property " + name);
        }
        return value;
    }

    private static int positiveIntProperty(String name, int defaultValue) {
        int value = Integer.parseInt(System.getProperty(name, Integer.toString(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long openFileDescriptorCount() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof UnixOperatingSystemMXBean unix) {
            return unix.getOpenFileDescriptorCount();
        }
        return -1;
    }

    private static void awaitNoMatchingProcesses(Path executable, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (matchingProcesses(executable).isEmpty()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("MyStem workers remain alive after " + timeout + ": " + matchingProcesses(executable));
    }

    private static List<ProcessHandle> matchingProcesses(Path executable) {
        return ProcessHandle.current()
                .descendants()
                .filter(ProcessHandle::isAlive)
                .filter(process -> sameExecutable(process, executable))
                .toList();
    }

    private static boolean sameExecutable(ProcessHandle process, Path executable) {
        return process.info().command().map(command -> {
            Path commandPath = Path.of(command).toAbsolutePath().normalize();
            try {
                commandPath = commandPath.toRealPath();
            } catch (IOException ignored) {
                // The process can exit between enumeration and path resolution.
            }
            return commandPath.equals(executable);
        }).orElse(false);
    }

    private record SoakMetrics(
            int concurrency, int requests, double throughputPerSecond, long p95Nanos, long p99Nanos) {}

    private static final class ProcessSampler implements AutoCloseable {
        private final Path executable;
        private final Set<Long> pids = ConcurrentHashMap.newKeySet();
        private final AtomicInteger maximumAlive = new AtomicInteger();
        private final AtomicInteger currentAlive = new AtomicInteger();
        private final AtomicBoolean running = new AtomicBoolean();
        private Thread thread;

        private ProcessSampler(Path executable) {
            this.executable = executable;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                throw new IllegalStateException("process sampler already started");
            }
            thread = Thread.ofPlatform().daemon().name("mystem4j-pool-process-sampler").start(() -> {
                while (running.get()) {
                    sampleNow();
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }

        private void sampleNow() {
            List<ProcessHandle> processes = matchingProcesses(executable);
            currentAlive.set(processes.size());
            maximumAlive.accumulateAndGet(processes.size(), Math::max);
            processes.forEach(process -> pids.add(process.pid()));
        }

        private int currentAlive() {
            return currentAlive.get();
        }

        private int maximumAlive() {
            return maximumAlive.get();
        }

        private int distinctPids() {
            return pids.size();
        }

        @Override
        public void close() {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(TimeUnit.SECONDS.toMillis(2));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while stopping process sampler", interrupted);
                }
                if (thread.isAlive()) {
                    throw new AssertionError("process sampler did not stop");
                }
            }
        }
    }
}
