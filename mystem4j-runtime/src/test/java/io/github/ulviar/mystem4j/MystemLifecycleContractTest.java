package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MystemLifecycleContractTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(value = MystemExecutionMode.class, names = {"ONE_SHOT_TEXT", "SESSION", "POOL"})
    void interruptReleasesProcessAndPreservesInterruptFlag(MystemExecutionMode mode) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (MystemClient client = client(mode)) {
            Thread request = new Thread(() -> {
                try {
                    client.analyze("wait");
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            request.start();
            try {
                awaitReady();
                request.interrupt();
                request.join(5_000);
                assertFalse(request.isAlive(), "interrupted request did not finish");
                assertTrue(failure.get() instanceof MystemException, () -> "unexpected failure: " + failure.get());
                assertTrue(interrupted.get(), "interrupt flag was lost");
                awaitProcessExit();
            } finally {
                Files.writeString(directory.resolve("release"), "release");
                request.interrupt();
                request.join(5_000);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = MystemExecutionMode.class, names = {"ONE_SHOT_TEXT", "SESSION", "POOL"})
    @SuppressWarnings("try") // Explicit concurrent close is the contract under test; the resource block ensures cleanup.
    void closeWaitsForAnActiveRequestAndReleasesResources(MystemExecutionMode mode) throws Exception {
        try (MystemClient client = client(mode);
                var executor = Executors.newFixedThreadPool(2)) {
            var request = executor.submit(() -> client.analyze("wait"));
            awaitReady();
            CountDownLatch closing = new CountDownLatch(1);
            var close = executor.submit(() -> {
                closing.countDown();
                client.close();
            });
            try {
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> close.get(150, TimeUnit.MILLISECONDS));
            } finally {
                Files.writeString(directory.resolve("release"), "release");
            }
            assertTrue(request.get(5, TimeUnit.SECONDS).output().contains("wait"));
            close.get(5, TimeUnit.SECONDS);
            awaitProcessExit();
            assertThrows(MystemClosedException.class, () -> client.analyze("late"));
        }
    }

    private MystemClient client(MystemExecutionMode mode) throws Exception {
        Path executable = FakeMystemExecutable.create(directory, "controlled",
                mode == MystemExecutionMode.ONE_SHOT_TEXT ? "controlledOneShot" : "controlledInteractive",
                directory.resolve("pid").toString(), directory.resolve("ready").toString(),
                directory.resolve("release").toString());
        MystemClientBuilder builder = Mystem.builder().executable(executable).requestTimeout(Duration.ofSeconds(10));
        return switch (mode) {
            case ONE_SHOT_TEXT -> builder.build();
            case SESSION -> builder.session().build();
            case POOL -> builder.pooled(pool -> pool.maxSize(1).warmupSize(0).minIdle(0)).build();
            default -> throw new IllegalArgumentException("unsupported test mode");
        };
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(directory.resolve("ready")) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(Files.exists(directory.resolve("ready")), "child did not receive request");
    }

    private void awaitProcessExit() throws Exception {
        long pid = Long.parseLong(Files.readString(directory.resolve("pid")).strip());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "child remains alive: " + pid);
    }
}
