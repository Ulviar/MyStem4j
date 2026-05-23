package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Objects;

public record MystemFileContentResult(
        Path input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    public MystemFileContentResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
