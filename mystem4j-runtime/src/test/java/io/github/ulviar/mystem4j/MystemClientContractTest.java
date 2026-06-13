package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MystemClientContractTest {
    @Test
    void analyzeAllPreservesInputOrderAndDelegatesToAnalyze() {
        RecordingClient client = new RecordingClient();

        List<MystemRawResult> results = client.analyzeAll(List.of("one", "two", "three"));

        assertEquals(List.of("one", "two", "three"), client.requests);
        assertEquals(List.of("ONE", "TWO", "THREE"), results.stream().map(MystemRawResult::output).toList());
    }

    @Test
    void customClientsDefaultToUnknownExecutionProfileAndOutputFormat() {
        RecordingClient client = new RecordingClient();

        assertEquals(MystemClientExecutionProfile.UNKNOWN, client.executionProfile());
        assertEquals(Optional.empty(), client.outputFormat());
    }

    private static final class RecordingClient implements MystemClient {
        private final ArrayList<String> requests = new ArrayList<>();

        @Override
        public MystemRawResult analyze(String text) {
            requests.add(text);
            return new MystemRawResult(text, text.toUpperCase(java.util.Locale.ROOT), MystemOutputFormat.JSON, stats());
        }

        @Override
        public MystemFileContentResult analyzeFile(Path input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MystemFileResult analyzeFile(Path input, Path output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}

        private static MystemRequestStats stats() {
            return new MystemRequestStats(
                    Duration.ZERO,
                    MystemExecutionMode.ONE_SHOT_TEXT,
                    0,
                    0,
                    0,
                    0);
        }
    }
}
