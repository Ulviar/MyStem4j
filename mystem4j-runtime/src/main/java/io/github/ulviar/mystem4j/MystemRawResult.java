package io.github.ulviar.mystem4j;

import java.util.Objects;

/**
 * Raw output of a successful text request. No morphology parsing is performed.
 *
 * @param input exact caller-supplied text
 * @param output decoded process stdout, including the response line ending in session/pool mode
 * @param format configured MyStem output format
 * @param stats sizes and timing for this request
 */
public record MystemRawResult(String input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    /**
     * Creates a text result.
     *
     * @throws NullPointerException when any component is null
     */
    public MystemRawResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stats, "stats");
    }
}
