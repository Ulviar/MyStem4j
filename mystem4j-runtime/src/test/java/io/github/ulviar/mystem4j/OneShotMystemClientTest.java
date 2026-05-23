package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.ulviar.icli.command.CommandExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OneShotMystemClientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesStringWithOneShotProcess() throws IOException {
        Path executable = fakeMystem();
        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertEquals("[{\"text\":\"Мама мыла раму.\"}]\n", result.output());
            assertEquals(MystemOutputFormat.JSON, result.format());
            assertEquals(MystemExecutionMode.ONE_SHOT_TEXT, result.stats().mode());
        }
    }

    @Test
    void analyzesFileToStdout() throws IOException {
        Path executable = fakeMystem();
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "file input", StandardCharsets.UTF_8);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            MystemFileContentResult result = client.analyzeFile(input);

            assertEquals("file input", result.output());
            assertEquals(MystemExecutionMode.ONE_SHOT_FILE, result.stats().mode());
        }
    }

    @Test
    void analyzesFileToOutputFile() throws IOException {
        Path executable = fakeMystem();
        Path input = temporaryDirectory.resolve("input.txt");
        Path output = temporaryDirectory.resolve("output.txt");
        Files.writeString(input, "file input", StandardCharsets.UTF_8);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            MystemFileResult result = client.analyzeFile(input, output);

            assertEquals(output, result.output());
            assertEquals("file input", Files.readString(output, StandardCharsets.UTF_8));
            assertTrue(result.stats().outputBytes() > 0);
        }
    }

    @Test
    void overwritesExistingOutputFile() throws IOException {
        Path executable = fakeMystem();
        Path input = temporaryDirectory.resolve("input.txt");
        Path output = temporaryDirectory.resolve("output.txt");
        Files.writeString(input, "new content", StandardCharsets.UTF_8);
        Files.writeString(output, "old content", StandardCharsets.UTF_8);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            client.analyzeFile(input, output);

            assertEquals("new content", Files.readString(output, StandardCharsets.UTF_8));
        }
    }

    @Test
    void rejectsDirectoryAsOutputFile() throws IOException {
        Path executable = fakeMystem();
        Path input = temporaryDirectory.resolve("input.txt");
        Path output = temporaryDirectory.resolve("output");
        Files.writeString(input, "file input", StandardCharsets.UTF_8);
        Files.createDirectories(output);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, output));
        }
    }

    @Test
    void rejectsSameInputAndOutputFile() throws IOException {
        Path executable = fakeMystem();
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "file input", StandardCharsets.UTF_8);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, input));
        }
    }

    @Test
    void rejectsRequestsAfterClose() throws IOException {
        Path executable = fakeMystem();
        MystemClient client = Mystem.builder().executable(executable).build();
        client.close();

        assertThrows(MystemClosedException.class, () -> client.analyze("text"));
    }

    @Test
    void mapsTimeout() throws IOException {
        Path executable = script(
                "sleeping-mystem",
                """
                #!/bin/sh
                sleep 5
                """);

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofMillis(100))
                .build()) {
            assertThrows(MystemRequestTimeoutException.class, () -> client.analyze("text"));
        }
    }

    @Test
    void mapsExitCodeAndStderr() throws IOException {
        Path executable = script(
                "failing-mystem",
                """
                #!/bin/sh
                echo "bad mystem" >&2
                exit 7
                """);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            MystemProcessException error =
                    assertThrows(MystemProcessException.class, () -> client.analyze("text"));

            assertEquals(7, error.exitCode().orElseThrow());
            assertTrue(error.stderr().contains("bad mystem"));
        }
    }

    @Test
    void mapsOutputLimit() throws IOException {
        Path executable = script(
                "large-output-mystem",
                """
                #!/bin/sh
                printf '0123456789abcdefghijklmnopqrstuvwxyz'
                """);

        try (MystemClient client = Mystem.builder().executable(executable).maxResponseBytes(8).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("text"));
        }
    }

    @Test
    void mapsProcessStartupFailure() throws IOException {
        Path executable = temporaryDirectory.resolve("missing-interpreter");
        Files.writeString(
                executable,
                """
                #!/definitely/missing/interpreter
                """,
                StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            assertThrows(MystemStartupException.class, () -> client.analyze("text"));
        }
    }

    @Test
    void mapsPostStartupExecutionFailureAsProtocolFailure() throws IOException {
        CommandExecutionException commandFailure = new CommandExecutionException(
                CommandExecutionException.Reason.RUNTIME_FAILURE, "Could not write command stdin");

        MystemException mapped =
                MystemProtocolFailureMapper.map(commandFailure, "Failed to execute MyStem one-shot process");

        assertEquals(MystemProtocolException.class, mapped.getClass());
        assertSame(commandFailure, mapped.getCause());
    }

    private Path fakeMystem() throws IOException {
        return script(
                "fake-mystem",
                """
                #!/bin/sh
                last=""
                previous=""
                for argument in "$@"; do
                  previous="$last"
                  last="$argument"
                done
                if [ -n "$previous" ] && [ -f "$previous" ]; then
                  cp "$previous" "$last"
                  exit 0
                fi
                if [ -n "$last" ] && [ -f "$last" ]; then
                  cat "$last"
                  exit 0
                fi
                input="$(cat)"
                printf '[{"text":"%s"}]\\n' "$input"
                """);
    }

    private Path script(String name, String body) throws IOException {
        Path executable = temporaryDirectory.resolve(name);
        Files.writeString(executable, body, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        return executable;
    }
}
