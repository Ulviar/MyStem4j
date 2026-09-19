package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Successful execution of a JSON smoke request against a MyStem executable.
 *
 * <p>The probe checks basic response structure; it does not determine the MyStem version or verify
 * linguistic quality. Its elapsed time is the one-shot request duration, excluding executable resolution
 * and response validation.
 *
 * @param executable resolved executable path
 * @param elapsed non-negative duration of the smoke request
 * @param format output format used by the probe ({@link MystemOutputFormat#JSON})
 * @param output raw decoded smoke-request output
 */
public record MystemProbeResult(Path executable, Duration elapsed, MystemOutputFormat format, String output) {
    /**
     * Creates probe metadata.
     *
     * @throws NullPointerException when any component is null
     * @throws IllegalArgumentException when elapsed time is negative
     */
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
