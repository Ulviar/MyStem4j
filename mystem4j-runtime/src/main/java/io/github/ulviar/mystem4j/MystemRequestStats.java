package io.github.ulviar.mystem4j;

import java.time.Duration;

/**
 * Timing and size measurements of a successful request; failed requests return no statistics.
 *
 * <p>Elapsed time has mode-specific boundaries:
 * <ul>
 * <li>One-shot text/file: process execution, including startup, output collection and process cleanup.
 * Runtime argument validation and file-size inspection are outside this measurement.</li>
 * <li>Session: from immediately before sending the request until its response is returned. Waiting for
 * another caller to release the client's synchronization monitor is excluded, as is process startup.</li>
 * <li>Pool: includes FIFO admission and worker acquisition as well as request execution. Process creation
 * needed during acquisition is included; initial pool warmup during client construction is excluded.</li>
 * </ul>
 *
 * <p>Character counts are Java UTF-16 code units, not Unicode code points. Text input counts describe the
 * caller's payload and exclude any protocol newline added by the runtime. Captured output counts include
 * line endings. Session/pool responses normalize the line terminator to a single LF before counting.
 * Input bytes use the configured encoding. One-shot output bytes count captured raw bytes; session/pool
 * output bytes count the decoded response re-encoded with that encoding, so line-ending normalization or
 * replacement of malformed input can make them differ from bytes originally written by the process.
 *
 * <p>For file requests, file byte counts are inspected after successful execution and may be {@code -1}
 * if unavailable. Input character counts are {@code -1}; output character counts are also {@code -1} when
 * output is written directly to a file. Concurrent external file changes can affect reported file sizes.
 *
 * @param elapsed non-negative elapsed time with the boundaries described above
 * @param mode execution mode of this request; file requests always use {@link MystemExecutionMode#ONE_SHOT_FILE}
 * @param inputChars input UTF-16 code units, or {@code -1} when unknown
 * @param inputBytes encoded text payload bytes or input file size, or {@code -1} when unknown
 * @param outputChars decoded output UTF-16 code units, or {@code -1} when unknown
 * @param outputBytes output bytes with the mode-specific semantics above, or {@code -1} when unknown
 */
public record MystemRequestStats(
        Duration elapsed,
        MystemExecutionMode mode,
        long inputChars,
        long inputBytes,
        long outputChars,
        long outputBytes) {
    /**
     * Creates request measurements.
     *
     * <p>Zero denotes an observed empty payload or zero elapsed time; {@code -1} denotes an unknown size.
     * Values are checked individually, without attempting to infer or verify relationships between
     * character counts, byte counts, or the execution mode.
     *
     * @throws IllegalArgumentException when elapsed is null or negative, mode is null, or a size is below {@code -1}
     */
    public MystemRequestStats {
        if (elapsed == null || elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be null or negative");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (inputChars < -1 || inputBytes < -1 || outputChars < -1 || outputBytes < -1) {
            throw new IllegalArgumentException("sizes must be non-negative or -1 when unknown");
        }
    }

    static MystemRequestStats oneShotText(
            Duration elapsed, long inputChars, long inputBytes, long outputChars, long outputBytes) {
        return new MystemRequestStats(
                elapsed,
                MystemExecutionMode.ONE_SHOT_TEXT,
                inputChars,
                inputBytes,
                outputChars,
                outputBytes);
    }

    static MystemRequestStats oneShotFile(
            Duration elapsed, long inputChars, long inputBytes, long outputChars, long outputBytes) {
        return new MystemRequestStats(
                elapsed,
                MystemExecutionMode.ONE_SHOT_FILE,
                inputChars,
                inputBytes,
                outputChars,
                outputBytes);
    }
}
