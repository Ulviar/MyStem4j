package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Metadata for a successful request that writes MyStem output directly to a file.
 *
 * <p>All built-in client modes execute file requests in a separate one-shot process. The result does not
 * read either file into memory and does not take ownership of either path.
 *
 * @param input caller-supplied input file path, not necessarily absolute
 * @param output caller-supplied output file path, not necessarily absolute
 * @param format configured MyStem output format
 * @param stats one-shot file request timing and file sizes; character counts are unknown
 */
public record MystemFileResult(Path input, Path output, MystemOutputFormat format, MystemRequestStats stats) {
    /**
     * Creates a file result.
     *
     * @throws NullPointerException when any component is null
     */
    public MystemFileResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
