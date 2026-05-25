package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MystemPoolOptionsTest {
    @Test
    void buildsConfiguredPoolOptions() {
        MystemPoolOptions options = MystemPoolOptions.builder()
                .maxSize(4)
                .warmupSize(2)
                .minIdle(1)
                .acquireTimeout(Duration.ofMillis(500))
                .hookTimeout(Duration.ofMillis(700))
                .maxRequestsPerWorker(100)
                .maxWorkerAge(Duration.ofMinutes(5))
                .backgroundReplenishment(false)
                .build();

        assertEquals(4, options.maxSize());
        assertEquals(2, options.warmupSize());
        assertEquals(1, options.minIdle());
        assertEquals(Duration.ofMillis(500), options.acquireTimeout());
        assertEquals(Duration.ofMillis(700), options.hookTimeout());
        assertEquals(100, options.maxRequestsPerWorker());
        assertEquals(Duration.ofMinutes(5), options.maxWorkerAge());
        assertEquals(false, options.backgroundReplenishment());
    }

    @Test
    void rejectsInvalidPoolInvariants() {
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().maxSize(0).build());
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().maxSize(1).warmupSize(2).build());
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().maxSize(1).minIdle(2).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MystemPoolOptions.builder().acquireTimeout(Duration.ZERO).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MystemPoolOptions.builder().hookTimeout(Duration.ZERO).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MystemPoolOptions.builder().maxRequestsPerWorker(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MystemPoolOptions.builder().maxWorkerAge(Duration.ofMillis(-1)).build());
    }

    @Test
    void rejectsNullDurations() {
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().acquireTimeout(null).build());
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().hookTimeout(null).build());
        assertThrows(IllegalArgumentException.class, () -> MystemPoolOptions.builder().maxWorkerAge(null).build());
    }
}
