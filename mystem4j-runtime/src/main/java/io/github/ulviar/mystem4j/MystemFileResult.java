package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Objects;

public record MystemFileResult(Path input, Path output, MystemOutputFormat format, MystemRequestStats stats) {
    public MystemFileResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
