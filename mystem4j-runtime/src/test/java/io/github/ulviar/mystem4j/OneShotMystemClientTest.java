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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OneShotMystemClientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesStringWithOneShotProcess() throws IOException {
        Path executable = fakeMystem();
        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            assertEquals(MystemClientExecutionProfile.ONE_SHOT_PROCESS_PER_REQUEST, client.executionProfile());
            assertEquals(Optional.of(MystemOutputFormat.JSON), client.outputFormat());

            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertEquals("[{\"text\":\"Мама мыла раму.\"}]\n", result.output());
            assertEquals(MystemOutputFormat.JSON, result.format());
            assertEquals(MystemExecutionMode.ONE_SHOT_TEXT, result.stats().mode());
        }
    }

    @Test
    void exposesConfiguredOutputFormat() throws IOException {
        Path executable = fakeMystem();
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().format(MystemOutputFormat.TEXT).build())
                .build()) {
            assertEquals(Optional.of(MystemOutputFormat.TEXT), client.outputFormat());
        }
    }

    @Test
    void builderRejectsUnreadableFixlist() throws IOException {
        Path executable = fakeMystem();
        MystemOptions options = MystemOptions.builder()
                .fixlist(temporaryDirectory.resolve("missing.txt"))
                .build();

        assertThrows(
                MystemInvalidOptionsException.class,
                () -> Mystem.builder().executable(executable).options(options).build());
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
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "sleeping-mystem", "sleep");

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofMillis(100))
                .build()) {
            assertThrows(MystemRequestTimeoutException.class, () -> client.analyze("text"));
        }
    }

    @Test
    void mapsExitCodeAndStderr() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "failing-mystem", "fail", "7", "bad mystem");

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            MystemProcessException error =
                    assertThrows(MystemProcessException.class, () -> client.analyze("text"));

            assertEquals(7, error.exitCode().orElseThrow());
            assertTrue(error.stderr().contains("bad mystem"));
        }
    }

    @Test
    void mapsOutputLimit() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "large-output-mystem", "largeOutput");

        try (MystemClient client = Mystem.builder().executable(executable).maxResponseBytes(8).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("text"));
        }
    }

    @Test
    void appliesConfiguredNonUtf8EncodingToTextInputAndOutput() throws IOException {
        Path executable = cp1251EchoMystem();
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().encoding(MystemEncoding.CP1251).build())
                .build()) {
            MystemRawResult result = client.analyze("Привет");

            assertEquals("[{\"text\":\"Привет\"}]\n", result.output());
        }
    }

    @Test
    void mapsBrokenExecutableProcessFailure() throws IOException {
        Path executable = FakeMystemExecutable.brokenExecutable(temporaryDirectory, "broken-mystem");

        try (MystemClient client = Mystem.builder().executable(executable).build()) {
            assertThrows(MystemProcessException.class, () -> client.analyze("text"));
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
        return FakeMystemExecutable.create(temporaryDirectory, "fake-mystem", "echo");
    }

    private Path cp1251EchoMystem() throws IOException {
        return FakeMystemExecutable.create(temporaryDirectory, "cp1251-echo-mystem", "cp1251Echo");
    }
}
