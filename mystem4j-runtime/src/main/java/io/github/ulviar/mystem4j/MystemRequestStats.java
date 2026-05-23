package io.github.ulviar.mystem4j;

import java.time.Duration;
import java.util.OptionalInt;

public record MystemRequestStats(
        Duration elapsed,
        MystemExecutionMode mode,
        long inputChars,
        long inputBytes,
        long outputChars,
        long outputBytes,
        OptionalInt workerId,
        boolean workerRestarted) {
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
        if (workerId == null) {
            throw new IllegalArgumentException("workerId must not be null");
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
                outputBytes,
                OptionalInt.empty(),
                false);
    }

    static MystemRequestStats oneShotFile(
            Duration elapsed, long inputChars, long inputBytes, long outputChars, long outputBytes) {
        return new MystemRequestStats(
                elapsed,
                MystemExecutionMode.ONE_SHOT_FILE,
                inputChars,
                inputBytes,
                outputChars,
                outputBytes,
                OptionalInt.empty(),
                false);
    }
}
