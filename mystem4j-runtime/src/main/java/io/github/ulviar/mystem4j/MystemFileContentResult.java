package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raw output captured from a successful file request.
 *
 * <p>All built-in client modes execute file requests in a separate one-shot process. The output is captured
 * in memory and remains available after client closure or deletion of the input file. The result neither
 * keeps a file handle open nor takes ownership of the input file.
 *
 * @param input caller-supplied input file path, not necessarily absolute
 * @param output decoded process stdout, including whitespace and line endings
 * @param format configured MyStem output format
 * @param stats one-shot file request sizes and timing
 */
public record MystemFileContentResult(
        Path input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    /**
     * Creates a captured file result.
     *
     * <p>This constructor checks non-null components only; it does not access the input file or validate
     * the output against its declared format and statistics.
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
