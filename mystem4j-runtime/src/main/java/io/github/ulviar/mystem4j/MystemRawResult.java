package io.github.ulviar.mystem4j;

import java.util.Objects;

/**
 * Raw output of a successful text request. No morphology parsing is performed.
 *
 * <p>Runtime clients decode stdout using the configured {@link MystemEncoding}. One-shot output retains
 * captured line endings; session/pool output contains one response line terminated by {@code '\n'},
 * with a CRLF response terminator normalized to LF. Selecting JSON does not validate JSON syntax. Both strings
 * remain available after the producing client is closed.
 *
 * @param input exact caller-supplied text
 * @param output decoded process stdout, with the response terminator normalized to LF in session/pool mode
 * @param format configured MyStem output format
 * @param stats sizes and timing for this request
 */
public record MystemRawResult(String input, String output, MystemOutputFormat format, MystemRequestStats stats) {
    /**
     * Creates a text result.
     *
     * <p>Only non-null components are checked. This constructor does not parse output or verify that its
     * contents, format, or sizes agree with the supplied statistics.
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
