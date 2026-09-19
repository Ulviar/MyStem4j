package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

class MystemLimitContractTest {
    @TempDir
    Path directory;

    enum Mode { ONE_SHOT, SESSION, POOL }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void acceptsExactPayloadLimitsWithoutCountingProtocolNewline(Mode mode) throws IOException {
        try (MystemClient client = builder(mode).maxRequestChars(6).maxRequestBytes(12).build()) {
            MystemRawResult result = client.analyze("Привет");
            assertEquals("[{\"text\":\"Привет\"}]\n", result.output());
            assertEquals(6, result.stats().inputChars());
            assertEquals(12, result.stats().inputBytes());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void rejectedCharacterLimitDoesNotPoisonTheClient(Mode mode) throws IOException {
        try (MystemClient client = builder(mode).maxRequestChars(3).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("long"));
            assertEquals("[{\"text\":\"ok\"}]\n", client.analyze("ok").output());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void rejectedByteLimitDoesNotPoisonTheClient(Mode mode) throws IOException {
        try (MystemClient client = builder(mode).maxRequestBytes(3).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("яя"));
            assertEquals("[{\"text\":\"ok\"}]\n", client.analyze("ok").output());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void capturedResponseCharacterLimitAppliesInEveryMode(Mode mode) throws IOException {
        try (MystemClient client = builder(mode).maxResponseChars(8).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("ok"));
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void responseBoundaryIncludesTheFinalNewline(Mode mode) throws IOException {
        String response = "[{\"text\":\"ok\"}]\n";
        try (MystemClient client = builder(mode).maxResponseChars(response.length()).build()) {
            assertEquals(response, client.analyze("ok").output());
        }
        try (MystemClient client = builder(mode).maxResponseChars(response.length() - 1).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("ok"));
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void fileStdoutHonorsCharacterLimit(Mode mode) throws IOException {
        Path input = Files.writeString(directory.resolve("input.txt"), "12345");
        try (MystemClient client = builder(mode).maxResponseChars(4).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyzeFile(input));
        }
    }

    private MystemClientBuilder builder(Mode mode) throws IOException {
        Path executable = FakeMystemExecutable.create(directory, "limits-" + mode,
                mode == Mode.ONE_SHOT ? "echo" : "interactiveEcho");
        MystemClientBuilder builder = Mystem.builder().executable(executable);
        return switch (mode) {
            case ONE_SHOT -> builder;
            case SESSION -> builder.session();
            case POOL -> builder.pooled(pool -> pool.maxSize(1).warmupSize(1));
        };
    }
}
