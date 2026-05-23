package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record MystemProbeResult(Path executable, Duration elapsed, MystemOutputFormat format, String output) {
    public MystemProbeResult {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(output, "output");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
    }
}
