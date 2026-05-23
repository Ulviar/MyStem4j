package io.github.ulviar.mystem4j;

import java.util.Objects;

public record MystemRawResult(String input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    public MystemRawResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
