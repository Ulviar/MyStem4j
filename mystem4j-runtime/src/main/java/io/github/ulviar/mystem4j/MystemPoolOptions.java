package io.github.ulviar.mystem4j;

import java.time.Duration;

/**
 * Pool configuration for pooled MyStem JSON-line sessions.
 */
public record MystemPoolOptions(
        int maxSize,
        int warmupSize,
        int minIdle,
        Duration acquireTimeout,
        Duration hookTimeout,
        int maxRequestsPerWorker,
        Duration maxWorkerAge,
        boolean backgroundReplenishment) {
    public MystemPoolOptions {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        if (warmupSize < 0 || warmupSize > maxSize) {
            throw new IllegalArgumentException("warmupSize must be in [0, maxSize]");
        }
        if (minIdle < 0 || minIdle > maxSize) {
            throw new IllegalArgumentException("minIdle must be in [0, maxSize]");
        }
        if (acquireTimeout == null || acquireTimeout.isNegative() || acquireTimeout.isZero()) {
            throw new IllegalArgumentException("acquireTimeout must be positive");
        }
        if (hookTimeout == null || hookTimeout.isNegative() || hookTimeout.isZero()) {
            throw new IllegalArgumentException("hookTimeout must be positive");
        }
        if (maxRequestsPerWorker <= 0) {
            throw new IllegalArgumentException("maxRequestsPerWorker must be positive");
        }
        if (maxWorkerAge == null || maxWorkerAge.isNegative()) {
            throw new IllegalArgumentException("maxWorkerAge must be non-negative");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxSize = Runtime.getRuntime().availableProcessors();
        private int warmupSize;
        private int minIdle;
        private Duration acquireTimeout = Duration.ofSeconds(2);
        private Duration hookTimeout = Duration.ofSeconds(2);
        private int maxRequestsPerWorker = Integer.MAX_VALUE;
        private Duration maxWorkerAge = Duration.ZERO;
        private boolean backgroundReplenishment = true;

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder warmupSize(int warmupSize) {
            this.warmupSize = warmupSize;
            return this;
        }

        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
            return this;
        }

        public Builder hookTimeout(Duration hookTimeout) {
            this.hookTimeout = hookTimeout;
            return this;
        }

        public Builder maxRequestsPerWorker(int maxRequestsPerWorker) {
            this.maxRequestsPerWorker = maxRequestsPerWorker;
            return this;
        }

        public Builder maxWorkerAge(Duration maxWorkerAge) {
            this.maxWorkerAge = maxWorkerAge;
            return this;
        }

        public Builder backgroundReplenishment(boolean backgroundReplenishment) {
            this.backgroundReplenishment = backgroundReplenishment;
            return this;
        }

        public MystemPoolOptions build() {
            return new MystemPoolOptions(
                    maxSize,
                    warmupSize,
                    minIdle,
                    acquireTimeout,
                    hookTimeout,
                    maxRequestsPerWorker,
                    maxWorkerAge,
                    backgroundReplenishment);
        }
    }
}
