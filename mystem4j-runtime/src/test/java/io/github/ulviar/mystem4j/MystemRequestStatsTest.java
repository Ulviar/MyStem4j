package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MystemRequestStatsTest {
    @Test
    void acceptsUnknownSizes() {
        MystemRequestStats stats = new MystemRequestStats(
                Duration.ofMillis(3),
                MystemExecutionMode.POOL,
                -1,
                -1,
                -1,
                -1);

        assertEquals(Duration.ofMillis(3), stats.elapsed());
        assertEquals(MystemExecutionMode.POOL, stats.mode());
        assertEquals(-1, stats.inputChars());
        assertEquals(-1, stats.outputBytes());
    }

    @Test
    void rejectsInvalidStatsInvariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemRequestStats(
                        null,
                        MystemExecutionMode.ONE_SHOT_TEXT,
                        0,
                        0,
                        0,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemRequestStats(
                        Duration.ofNanos(-1),
                        MystemExecutionMode.ONE_SHOT_TEXT,
                        0,
                        0,
                        0,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemRequestStats(
                        Duration.ZERO,
                        null,
                        0,
                        0,
                        0,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MystemRequestStats(
                        Duration.ZERO,
                        MystemExecutionMode.ONE_SHOT_TEXT,
                        -2,
                        0,
                        0,
                        0));
    }
}
