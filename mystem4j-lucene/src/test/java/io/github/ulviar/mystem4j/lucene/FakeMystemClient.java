package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestStats;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

final class FakeMystemClient implements MystemClient {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private final Function<String, String> output;
    private final ArrayList<String> requests = new ArrayList<>();
    private boolean closed;
    private int closeCount;

    FakeMystemClient(Function<String, String> output) {
        this.output = output;
    }

    static FakeMystemClient echo() {
        return new FakeMystemClient(input -> input.isEmpty()
                ? "[]"
                : """
                [{"analysis":[],"text":%s}]
                """.formatted(jsonString(input)));
    }

    @Override
    public MystemRawResult analyze(String text) {
        requests.add(text);
        String rawOutput = output.apply(text);
        return new MystemRawResult(
                text,
                rawOutput,
                MystemOutputFormat.JSON,
                new MystemRequestStats(
                        Duration.ZERO,
                        MystemExecutionMode.ONE_SHOT_TEXT,
                        text.length(),
                        -1,
                        rawOutput.length(),
                        -1,
                        OptionalInt.empty(),
                        false));
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
    public void close() {
        closed = true;
        closeCount++;
    }

    boolean isClosed() {
        return closed;
    }

    int closeCount() {
        return closeCount;
    }

    List<String> requests() {
        return List.copyOf(requests);
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        appendUnicodeEscape(result, character);
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        result.append('"');
        return result.toString();
    }

    private static void appendUnicodeEscape(StringBuilder result, char character) {
        result.append("\\u");
        result.append(HEX_DIGITS[(character >>> 12) & 0xF]);
        result.append(HEX_DIGITS[(character >>> 8) & 0xF]);
        result.append(HEX_DIGITS[(character >>> 4) & 0xF]);
        result.append(HEX_DIGITS[character & 0xF]);
    }
}
