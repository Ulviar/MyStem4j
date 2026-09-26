package io.github.ulviar.mystem4j;

import java.time.Duration;

/**
 * Immutable configuration for pooled MyStem JSON-line sessions.
 *
 * <p>Use {@link MystemClientBuilder#pooled(MystemPoolOptions)} to apply a configuration. Instances can be
 * shared between builders and threads. The default starts workers on demand, up to the number of available
 * processors, capped at 256; {@link #warmupSize()} and {@link #minIdle()} both default to zero.
 *
 * <p>Capacity limits text requests only. File requests use separate one-shot processes and can therefore
 * start additional processes beyond {@link #maxSize()}. Text requests wait in FIFO admission order before
 * acquiring a worker. {@link #acquireTimeout()} bounds each of these two stages separately; the execution
 * timeout configured by {@link MystemClientBuilder#requestTimeout(Duration)} starts afterward.
 */
public final class MystemPoolOptions {
    private final int maxSize;
    private final int warmupSize;
    private final int minIdle;
    private final Duration acquireTimeout;
    private final Duration hookTimeout;
    private final int maxRequestsPerWorker;
    private final Duration maxWorkerAge;
    private final boolean backgroundReplenishment;

    private MystemPoolOptions(Builder builder) {
        maxSize = builder.maxSize;
        warmupSize = builder.warmupSize;
        minIdle = builder.minIdle;
        acquireTimeout = builder.acquireTimeout;
        hookTimeout = builder.hookTimeout;
        maxRequestsPerWorker = builder.maxRequestsPerWorker;
        maxWorkerAge = builder.maxWorkerAge;
        backgroundReplenishment = builder.backgroundReplenishment;
        if (maxSize <= 0 || maxSize > 256) {
            throw new IllegalArgumentException("maxSize must be in [1, 256]");
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

    /**
     * Returns the maximum number of live workers and concurrent admitted text requests.
     *
     * @return capacity in {@code [1, 256]}; defaults to {@link Runtime#availableProcessors()}, capped at 256
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Returns the number of workers started when the pool opens.
     *
     * @return initial worker count; defaults to {@code 0}
     */
    public int warmupSize() {
        return warmupSize;
    }

    /**
     * Returns the target number of idle workers kept available.
     *
     * <p>This is a replenishment target, not a reservation: busy workers count toward {@link #maxSize()},
     * and startup failures or active requests can leave fewer idle workers available.
     * The target is inactive when {@link #backgroundReplenishment()} is {@code false}.
     *
     * @return idle worker target in {@code [0, maxSize]}; defaults to {@code 0}
     */
    public int minIdle() {
        return minIdle;
    }

    /**
     * Returns the timeout applied separately to FIFO request admission and worker acquisition.
     *
     * @return positive timeout for each acquisition stage; defaults to two seconds
     */
    public Duration acquireTimeout() {
        return acquireTimeout;
    }

    /**
     * Returns the maximum time allowed for an individual worker health-check or reset hook.
     *
     * @return positive hook timeout; defaults to two seconds
     */
    public Duration hookTimeout() {
        return hookTimeout;
    }

    /**
     * Returns the number of requests a worker may serve before replacement.
     *
     * @return positive request limit; defaults to {@link Integer#MAX_VALUE}
     */
    public int maxRequestsPerWorker() {
        return maxRequestsPerWorker;
    }

    /**
     * Returns the age after which a worker becomes eligible for replacement.
     *
     * <p>Age-based rotation does not interrupt a request already running on that worker.
     *
     * @return non-negative age limit; defaults to zero, which disables age-based replacement
     */
    public Duration maxWorkerAge() {
        return maxWorkerAge;
    }

    /**
     * Returns whether idle workers may be replenished in the background.
     *
     * @return whether background replenishment is enabled; defaults to {@code true}
     */
    public boolean backgroundReplenishment() {
        return backgroundReplenishment;
    }

    /**
     * Creates a pool builder with lazy worker startup and a capacity of available processors, capped at 256.
     *
     * @return a new pool options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable pool configuration builder; validation occurs in {@link #build()}.
     *
     * <p>Setters store values without validation, allowing related bounds to be configured in either order.
     * Each build creates an independent immutable snapshot. Builder instances are not thread-safe.
     */
    public static final class Builder {
        private int maxSize = Math.min(256, Runtime.getRuntime().availableProcessors());
        private int warmupSize;
        private int minIdle;
        private Duration acquireTimeout = Duration.ofSeconds(2);
        private Duration hookTimeout = Duration.ofSeconds(2);
        private int maxRequestsPerWorker = Integer.MAX_VALUE;
        private Duration maxWorkerAge = Duration.ZERO;
        private boolean backgroundReplenishment = true;

        private Builder() {}

        /**
         * Sets the maximum number of live workers and admitted text requests.
         *
         * @param maxSize capacity in {@code [1, 256]}; defaults to available processors, capped at 256
         * @return this builder
         */
        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Sets the number of workers started when the pool opens.
         *
         * @param warmupSize count in {@code [0, maxSize]}; defaults to {@code 0}
         * @return this builder
         */
        public Builder warmupSize(int warmupSize) {
            this.warmupSize = warmupSize;
            return this;
        }

        /**
         * Sets the target number of idle workers kept available.
         *
         * @param minIdle count in {@code [0, maxSize]}; defaults to {@code 0}
         * @return this builder
         */
        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        /**
         * Sets the timeout for FIFO request admission and subsequent worker acquisition.
         *
         * @param acquireTimeout non-null positive timeout for each acquisition stage; defaults to two seconds
         * @return this builder
         */
        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
            return this;
        }

        /**
         * Sets the timeout for worker health-check and post-request reset hooks.
         *
         * @param hookTimeout non-null positive duration; defaults to two seconds
         * @return this builder
         */
        public Builder hookTimeout(Duration hookTimeout) {
            this.hookTimeout = hookTimeout;
            return this;
        }

        /**
         * Sets the number of requests a worker may serve before replacement.
         *
         * @param maxRequestsPerWorker positive request limit; defaults to {@link Integer#MAX_VALUE}
         * @return this builder
         */
        public Builder maxRequestsPerWorker(int maxRequestsPerWorker) {
            this.maxRequestsPerWorker = maxRequestsPerWorker;
            return this;
        }

        /**
         * Sets the age after which a worker becomes eligible for replacement.
         *
         * <p>This is a worker rotation policy, not a deadline that interrupts an active request.
         *
         * @param maxWorkerAge non-null, non-negative duration; zero (the default) disables age-based replacement
         * @return this builder
         */
        public Builder maxWorkerAge(Duration maxWorkerAge) {
            this.maxWorkerAge = maxWorkerAge;
            return this;
        }

        /**
         * Controls background replenishment of idle workers.
         * When disabled, {@link #minIdle(int)} is not maintained; explicit warmup and on-demand startup still apply.
         *
         * @param backgroundReplenishment whether to replenish in the background; defaults to {@code true}
         * @return this builder
         */
        public Builder backgroundReplenishment(boolean backgroundReplenishment) {
            this.backgroundReplenishment = backgroundReplenishment;
            return this;
        }

        /**
         * Creates an immutable, validated pool configuration.
         *
         * @return configured pool options
         * @throws IllegalArgumentException when capacity is outside {@code [1, 256]}, request count is not positive, warmup or idle
         *     counts are outside {@code [0, maxSize]}, either timeout is null or not positive, or worker age
         *     is null or negative
         */
        public MystemPoolOptions build() {
            return new MystemPoolOptions(this);
        }
    }

}
