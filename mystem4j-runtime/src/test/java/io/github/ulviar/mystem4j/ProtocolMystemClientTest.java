package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtocolMystemClientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesTextWithReusableSession() throws IOException {
        Path executable = fakeInteractiveMystem();
        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
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
        Path executable = script(
                "exiting-mystem",
                """
                #!/bin/sh
                while IFS= read -r input; do
                  echo "fatal mystem" >&2
                  exit 9
                done
                """);

        try (MystemClient client = Mystem.builder().executable(executable).session().build()) {
            MystemProcessException error = assertThrows(MystemProcessException.class, () -> client.analyze("one"));

            assertEquals(9, error.exitCode().orElseThrow());
            assertEquals("fatal mystem", error.stderr());
        }
    }

    @Test
    void mapsPooledAcquireTimeout() throws Exception {
        Path executable = script(
                "slow-interactive-mystem",
                """
                #!/bin/sh
                while IFS= read -r input; do
                  sleep 1
                  printf '[{"text":"%s"}]\\n' "$input"
                done
                """);

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
    void mapsSessionStartupFailure() throws IOException {
        Path executable = missingInterpreterScript();

        assertThrows(MystemStartupException.class, () -> Mystem.builder().executable(executable).session().build());
    }

    @Test
    void mapsPoolStartupFailure() throws IOException {
        Path executable = missingInterpreterScript();

        assertThrows(
                MystemStartupException.class,
                () -> Mystem.builder()
                        .executable(executable)
                        .pooled(pool -> pool.maxSize(1).warmupSize(1))
                        .build());
    }

    private Path fakeInteractiveMystem() throws IOException {
        return script(
                "fake-interactive-mystem",
                """
                #!/bin/sh
                while IFS= read -r input; do
                  printf '[{"text":"%s"}]\\n' "$input"
                done
                """);
    }

    private Path missingInterpreterScript() throws IOException {
        return script(
                "missing-interpreter",
                """
                #!/definitely/missing/interpreter
                """);
    }

    private Path script(String name, String body) throws IOException {
        Path executable = temporaryDirectory.resolve(name);
        Files.writeString(executable, body, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        return executable;
    }
}
