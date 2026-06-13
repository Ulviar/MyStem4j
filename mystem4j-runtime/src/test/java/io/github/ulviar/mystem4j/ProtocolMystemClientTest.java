package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtocolMystemClientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesTextWithReusableSession() throws IOException {
        Path executable = fakeInteractiveMystem();
        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
            assertEquals(MystemClientExecutionProfile.REUSABLE_SESSION, client.executionProfile());
            assertEquals(Optional.of(MystemOutputFormat.JSON), client.outputFormat());
            assertEquals(MystemExecutionMode.SESSION, client.analyze("one").stats().mode());
            assertEquals("[{\"text\":\"two\"}]\n", client.analyze("two").output());
        }
    }

    @Test
    void analyzesTextWithPool() throws IOException {
        Path executable = fakeInteractiveMystem();
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .pooled(pool -> pool.maxSize(2).warmupSize(1).minIdle(1).acquireTimeout(Duration.ofSeconds(1)))
                .build()) {
            assertEquals(MystemClientExecutionProfile.POOLED_SESSIONS, client.executionProfile());
            assertEquals(Optional.of(MystemOutputFormat.JSON), client.outputFormat());
            assertEquals(MystemExecutionMode.POOL, client.analyze("one").stats().mode());
            assertEquals("[{\"text\":\"two\"}]\n", client.analyze("two").output());
        }
    }

    @Test
    void rejectsNonJsonReusableSession() throws IOException {
        Path executable = fakeInteractiveMystem();
        assertThrows(
                MystemInvalidOptionsException.class,
                () -> Mystem.builder()
                        .executable(executable)
                        .options(MystemOptions.builder().format(MystemOutputFormat.TEXT).build())
                        .session()
                        .build());
    }

    @Test
    void rejectsNewLineEachWordInReusableSession() throws IOException {
        Path executable = fakeInteractiveMystem();

        assertThrows(
                MystemInvalidOptionsException.class,
                () -> Mystem.builder()
                        .executable(executable)
                        .options(MystemOptions.builder().newLineEachWord(true).build())
                        .session()
                        .build());
    }

    @Test
    void rejectsNewLineEachWordInPool() throws IOException {
        Path executable = fakeInteractiveMystem();

        assertThrows(
                MystemInvalidOptionsException.class,
                () -> Mystem.builder()
                        .executable(executable)
                        .options(MystemOptions.builder().newLineEachWord(true).build())
                        .pooled()
                        .build());
    }

    @Test
    void rejectsMultilineInputInReusableSession() throws IOException {
        Path executable = fakeInteractiveMystem();
        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("one\ntwo"));
        }
    }

    @Test
    void rejectsReusableRequestsAfterClose() throws IOException {
        Path executable = fakeInteractiveMystem();
        MystemClient client = Mystem.builder().executable(executable).session().build();
        client.close();

        assertThrows(MystemClosedException.class, () -> client.analyze("one"));
    }

    @Test
    void rejectsPooledRequestsAfterClose() throws IOException {
        Path executable = fakeInteractiveMystem();
        MystemClient client = Mystem.builder().executable(executable).pooled().build();
        client.close();

        assertThrows(MystemClosedException.class, () -> client.analyze("one"));
    }

    @Test
    void mapsEarlyProcessExitInReusableSession() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "exiting-mystem", "exitOnFirstRequest");

        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
            MystemProcessException error = assertThrows(MystemProcessException.class, () -> client.analyze("one"));

            assertEquals(9, error.exitCode().orElseThrow());
            assertEquals("fatal mystem", error.stderr());
        }
    }

    @Test
    void mapsPooledAcquireTimeout() throws Exception {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "slow-interactive-mystem", "slowInteractive");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .pooled(pool -> pool.maxSize(1).warmupSize(1).acquireTimeout(Duration.ofMillis(100)))
                .build()) {
            Future<MystemRawResult> firstRequest = executor.submit(() -> client.analyze("one"));
            Thread.sleep(150);

            assertThrows(MystemPoolExhaustedException.class, () -> client.analyze("two"));
            assertEquals("[{\"text\":\"one\"}]\n", firstRequest.get().output());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pooledClientReplacesWorkerAfterMaxRequests() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "pid-reporting-interactive-mystem", "pidInteractive");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .pooled(pool -> pool.maxSize(1)
                        .warmupSize(1)
                        .minIdle(1)
                        .maxRequestsPerWorker(1)
                        .acquireTimeout(Duration.ofSeconds(1)))
                .build()) {
            String firstPid = pidFromOutput(client.analyze("one").output());
            String secondPid = pidFromOutput(client.analyze("two").output());

            assertTrue(!firstPid.equals(secondPid), "pool should replace worker after maxRequestsPerWorker");
        }
    }

    @Test
    void pooledClientHandlesConcurrentRequests() throws Exception {
        Path executable = fakeInteractiveMystem();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofSeconds(1))
                .pooled(pool -> pool.maxSize(4).warmupSize(2).minIdle(1).acquireTimeout(Duration.ofSeconds(1)))
                .build()) {
            List<Future<String>> outputs = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                String input = "request-" + index;
                outputs.add(executor.submit(() -> client.analyze(input).output()));
            }

            for (int index = 0; index < outputs.size(); index++) {
                assertEquals("[{\"text\":\"request-" + index + "\"}]\n", outputs.get(index).get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pooledClientReplacesWorkerAfterCrash() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "crashing-interactive-mystem", "crashOnDie");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofSeconds(1))
                .pooled(pool -> pool.maxSize(1).warmupSize(1).minIdle(1).acquireTimeout(Duration.ofSeconds(1)))
                .build()) {
            assertThrows(MystemException.class, () -> client.analyze("die"));

            assertEquals("[{\"text\":\"ok\"}]\n", client.analyze("ok").output());
        }
    }

    @Test
    void reportsReusableSessionAsFailedAfterRequestTimeout() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "timeout-interactive-mystem", "slowInteractive");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofMillis(50))
                .session()
                .build()) {
            assertThrows(MystemRequestTimeoutException.class, () -> client.analyze("one"));
            assertThrows(MystemProtocolException.class, () -> client.analyze("two"));
        }
    }

    @Test
    void noisyReusableSessionFailsWithBoundedOutputLimit() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "noisy-interactive-mystem", "noisyInteractive");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .maxResponseBytes(256)
                .requestTimeout(Duration.ofSeconds(1))
                .session()
                .build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("one"));
        }
    }

    @Test
    void noisyPooledSessionFailsWithBoundedOutputLimit() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "noisy-pooled-mystem", "noisyInteractive");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .maxResponseBytes(256)
                .requestTimeout(Duration.ofSeconds(1))
                .pooled(pool -> pool.maxSize(1).warmupSize(1).acquireTimeout(Duration.ofSeconds(1)))
                .build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("one"));
        }
    }

    @Test
    void brokenExecutableSessionFailsOnRequest() throws IOException {
        Path executable = FakeMystemExecutable.brokenExecutable(temporaryDirectory, "broken-session-mystem");

        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
            assertThrows(MystemException.class, () -> client.analyze("one"));
        }
    }

    @Test
    void brokenExecutablePoolFailsOnRequest() throws IOException {
        Path executable = FakeMystemExecutable.brokenExecutable(temporaryDirectory, "broken-pool-mystem");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .pooled(pool -> pool.maxSize(1).warmupSize(1))
                .build()) {
            assertThrows(MystemException.class, () -> client.analyze("one"));
        }
    }

    private Path fakeInteractiveMystem() throws IOException {
        return FakeMystemExecutable.create(temporaryDirectory, "fake-interactive-mystem", "interactiveEcho");
    }

    private static String pidFromOutput(String output) {
        java.util.regex.Matcher matcher = Pattern.compile("pid:(\\d+)").matcher(output);
        if (!matcher.find()) {
            throw new AssertionError("No pid in output: " + output);
        }
        return matcher.group(1);
    }

}
