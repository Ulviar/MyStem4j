package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemPoolAdmissionTest {
    @TempDir
    Path directory;

    @RepeatedTest(10)
    void queuedCallerCannotBeOvertakenByARepeatingCaller() throws Exception {
        Path ready = directory.resolve("ready");
        Path release = directory.resolve("release");
        Path order = directory.resolve("order");
        Path executable = FakeMystemExecutable.create(directory, "gated", "gatedInteractive",
                ready.toString(), release.toString(), order.toString());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MystemClient client = Mystem.builder().executable(executable).requestTimeout(Duration.ofSeconds(5))
                .pooled(pool -> pool.maxSize(1).acquireTimeout(Duration.ofSeconds(5))).build()) {
            Thread repeating = new Thread(() -> {
                try {
                    client.analyze("hold");
                    client.analyze("hot");
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            Thread queued = new Thread(() -> {
                try {
                    client.analyze("queued");
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            repeating.start();
            try {
                await(() -> Files.exists(ready));
                queued.start();
                await(() -> queued.getState() == Thread.State.TIMED_WAITING);
            } finally {
                Files.writeString(release, "release");
                repeating.join(5000);
                queued.join(5000);
            }
            assertFalse(repeating.isAlive());
            assertFalse(queued.isAlive());
            assertEquals(null, failure.get());
            assertEquals(List.of("hold", "queued", "hot"), Files.readAllLines(order));
        }
    }

    @Test
    void interruptingQueuedCallerLeavesTheActiveWorkerUsable() throws Exception {
        Path ready = directory.resolve("ready");
        Path release = directory.resolve("release");
        Path order = directory.resolve("order");
        Path executable = FakeMystemExecutable.create(directory, "gated", "gatedInteractive",
                ready.toString(), release.toString(), order.toString());
        AtomicReference<Throwable> activeFailure = new AtomicReference<>();
        AtomicReference<Throwable> queuedFailure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (MystemClient client = Mystem.builder().executable(executable).requestTimeout(Duration.ofSeconds(5))
                .pooled(pool -> pool.maxSize(1).acquireTimeout(Duration.ofSeconds(5))).build()) {
            Thread active = new Thread(() -> {
                try {
                    client.analyze("hold");
                } catch (Throwable error) {
                    activeFailure.set(error);
                }
            });
            Thread queued = new Thread(() -> {
                try {
                    client.analyze("cancelled");
                } catch (Throwable error) {
                    queuedFailure.set(error);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            active.start();
            try {
                await(() -> Files.exists(ready));
                queued.start();
                await(() -> queued.getState() == Thread.State.TIMED_WAITING);
                queued.interrupt();
                queued.join(3000);
                assertFalse(queued.isAlive());
                assertTrue(queuedFailure.get() instanceof MystemProtocolException);
                assertTrue(interrupted.get());
            } finally {
                Files.writeString(release, "release");
                queued.interrupt();
                queued.join(5000);
                active.join(5000);
            }
            assertEquals(null, activeFailure.get());
            assertTrue(client.analyze("after").output().contains("after"));
            assertEquals(List.of("hold", "after"), Files.readAllLines(order));
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean(), "request did not reach the expected coordination point");
    }
}
