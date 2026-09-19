package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raw output captured from a successful file request.
 *
 * <p>All built-in client modes execute file requests in a separate one-shot process.
 *
 * @param input caller-supplied input file path, not necessarily absolute
 * @param output decoded process stdout
 * @param format configured MyStem output format
 * @param stats one-shot file request sizes and timing
 */
public record MystemFileContentResult(
        Path input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    /**
     * Creates a captured file result.
     *
     * @throws NullPointerException when any component is null
     */
    public MystemFileContentResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
