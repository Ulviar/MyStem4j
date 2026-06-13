package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.Icli;
import com.github.ulviar.icli.command.CharsetPolicy;
import com.github.ulviar.icli.session.PooledProtocolSession;
import com.github.ulviar.icli.session.PooledProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolSession;
import com.github.ulviar.icli.session.ProtocolSessionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Builder for MyStem runtime clients.
 *
 * <p>The default mode is one-shot. Call {@link #session()} for one long-lived JSON-line process or
 * {@link #pooled(Consumer)} for a thread-safe pool of JSON-line processes.
 */
public final class MystemClientBuilder {
    private enum Mode {
        ONE_SHOT,
        SESSION,
        POOL
    }

    private Optional<Path> executable = Optional.empty();
    private MystemOptions options = MystemOptions.builder().build();
    private boolean searchPath = true;
    private Mode mode = Mode.ONE_SHOT;
    private MystemPoolOptions poolOptions = MystemPoolOptions.builder().build();
    private Duration requestTimeout = Duration.ofSeconds(3);
    private Duration idleTimeout = Duration.ZERO;
    private int maxRequestChars = 1_000_000;
    private int maxRequestBytes = 4_000_000;
    private int maxResponseChars = 8_000_000;
    private int maxResponseBytes = 32_000_000;
    private boolean includeInputInDiagnostics;

    MystemClientBuilder() {}

    /**
     * Sets the executable path.
     *
     * @param executable executable path
     * @return this builder
     */
    public MystemClientBuilder executable(Path executable) {
        this.executable = Optional.of(Objects.requireNonNull(executable, "executable"));
        return this;
    }

    /**
     * Sets MyStem CLI options.
     *
     * @param options options
     * @return this builder
     */
    public MystemClientBuilder options(MystemOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        return this;
    }

    /**
     * Enables or disables PATH lookup when no explicit executable is configured.
     *
     * @param searchPath whether PATH lookup is enabled
     * @return this builder
     */
    public MystemClientBuilder searchPath(boolean searchPath) {
        this.searchPath = searchPath;
        return this;
    }

    /**
     * Sets the per-request timeout.
     *
     * @param requestTimeout timeout
     * @return this builder
     */
    public MystemClientBuilder requestTimeout(Duration requestTimeout) {
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * Sets the idle timeout for reusable or pooled workers.
     *
     * @param idleTimeout idle timeout, or {@link Duration#ZERO} to disable
     * @return this builder
     */
    public MystemClientBuilder idleTimeout(Duration idleTimeout) {
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        if (idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must be non-negative");
        }
        this.idleTimeout = idleTimeout;
        return this;
    }

    /**
     * Selects reusable JSON-line session mode.
     *
     * @return this builder
     */
    public MystemClientBuilder session() {
        this.mode = Mode.SESSION;
        return this;
    }

    /**
     * Selects pooled JSON-line session mode and configures pool options.
     *
     * @param configure pool options callback
     * @return this builder
     */
    public MystemClientBuilder pooled(Consumer<MystemPoolOptions.Builder> configure) {
        MystemPoolOptions.Builder builder = MystemPoolOptions.builder();
        Objects.requireNonNull(configure, "configure").accept(builder);
        this.poolOptions = builder.build();
        this.mode = Mode.POOL;
        return this;
    }

    /**
     * Selects pooled JSON-line session mode with explicit pool options.
     *
     * @param poolOptions pool options
     * @return this builder
     */
    public MystemClientBuilder pooled(MystemPoolOptions poolOptions) {
        this.poolOptions = Objects.requireNonNull(poolOptions, "poolOptions");
        this.mode = Mode.POOL;
        return this;
    }

    /**
     * Selects pooled JSON-line session mode with default pool options.
     *
     * @return this builder
     */
    public MystemClientBuilder pooled() {
        this.mode = Mode.POOL;
        return this;
    }

    /**
     * Sets maximum input characters for text requests.
     *
     * @param maxRequestChars maximum characters
     * @return this builder
     */
    public MystemClientBuilder maxRequestChars(int maxRequestChars) {
        if (maxRequestChars <= 0) {
            throw new IllegalArgumentException("maxRequestChars must be positive");
        }
        this.maxRequestChars = maxRequestChars;
        return this;
    }

    /**
     * Sets maximum input bytes for text requests.
     *
     * @param maxRequestBytes maximum bytes
     * @return this builder
     */
    public MystemClientBuilder maxRequestBytes(int maxRequestBytes) {
        if (maxRequestBytes <= 0) {
            throw new IllegalArgumentException("maxRequestBytes must be positive");
        }
        this.maxRequestBytes = maxRequestBytes;
        return this;
    }

    /**
     * Sets maximum decoded response characters for protocol-session reads.
     *
     * @param maxResponseChars maximum characters
     * @return this builder
     */
    public MystemClientBuilder maxResponseChars(int maxResponseChars) {
        if (maxResponseChars <= 0) {
            throw new IllegalArgumentException("maxResponseChars must be positive");
        }
        this.maxResponseChars = maxResponseChars;
        return this;
    }

    /**
     * Sets maximum captured response bytes.
     *
     * @param maxResponseBytes maximum bytes
     * @return this builder
     */
    public MystemClientBuilder maxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
        return this;
    }

    /**
     * Controls whether diagnostic messages may include full input.
     *
     * @param includeInputInDiagnostics whether input may be included
     * @return this builder
     */
    public MystemClientBuilder includeInputInDiagnostics(boolean includeInputInDiagnostics) {
        this.includeInputInDiagnostics = includeInputInDiagnostics;
        return this;
    }

    /**
     * Builds a configured client.
     *
     * @return client
     * @throws MystemExecutableNotFoundException when no executable can be resolved
     * @throws MystemInvalidOptionsException when the selected mode is incompatible with configured options
     * @throws MystemStartupException when a reusable session or pool cannot be started
     * @throws MystemException when session or pool startup fails with a protocol-level runtime error
     */
    public MystemClient build() {
        validateRuntimeOptions();
        Path resolvedExecutable = MystemExecutableResolver.resolve(executable, searchPath);
        OneShotMystemClient oneShotClient = newOneShotClient(resolvedExecutable);
        if (mode == Mode.ONE_SHOT) {
            return oneShotClient;
        }
        if (options.format() != MystemOutputFormat.JSON) {
            throw new MystemInvalidOptionsException("Reusable and pooled MyStem clients require JSON format.");
        }
        if (options.newLineEachWord()) {
            throw new MystemInvalidOptionsException(
                    "Reusable and pooled MyStem clients cannot use newLineEachWord because it breaks JSON-line request framing.");
        }
        if (mode == Mode.SESSION) {
            ProtocolSession<String, String> session;
            try {
                session = Icli.command(resolvedExecutable.toString())
                        .protocolSession(() -> new JsonLineMystemAdapter(maxResponseChars))
                        .withArgs(options.toArguments())
                        .withRequestTimeout(requestTimeout)
                        .withIdleTimeout(idleTimeout)
                        .withMaxRequestChars(maxRequestChars)
                        .withMaxRequestBytes(maxRequestBytes)
                        .withMaxResponseChars(maxResponseChars)
                        .withMaxResponseBytes(maxResponseBytes)
                        .withOutputBacklogLimit(maxResponseBytes)
                        .withCharsetPolicy(CharsetPolicy.replace(options.encoding().charset()))
                        .open();
            } catch (ProtocolSessionException error) {
                throw MystemProtocolFailureMapper.map(error);
            } catch (RuntimeException error) {
                throw new MystemStartupException("Failed to start reusable MyStem session.", error);
            }
            return new ReusableMystemClient(session, oneShotClient, options, requestTimeout);
        }

        PooledProtocolSession<String, String> pool;
        try {
            pool = Icli.command(resolvedExecutable.toString())
                    .protocolSession(() -> new JsonLineMystemAdapter(maxResponseChars))
                    .withArgs(options.toArguments())
                    .withRequestTimeout(requestTimeout)
                    .withIdleTimeout(idleTimeout)
                    .withMaxRequestChars(maxRequestChars)
                    .withMaxRequestBytes(maxRequestBytes)
                    .withMaxResponseChars(maxResponseChars)
                    .withMaxResponseBytes(maxResponseBytes)
                    .withOutputBacklogLimit(maxResponseBytes)
                    .withCharsetPolicy(CharsetPolicy.replace(options.encoding().charset()))
                    .pooled()
                    .withMaxSize(poolOptions.maxSize())
                    .withWarmupSize(poolOptions.warmupSize())
                    .withMinIdle(poolOptions.minIdle())
                    .withAcquireTimeout(poolOptions.acquireTimeout())
                    .withHookTimeout(poolOptions.hookTimeout())
                    .withMaxRequestsPerWorker(poolOptions.maxRequestsPerWorker())
                    .withMaxWorkerAge(poolOptions.maxWorkerAge())
                    .withBackgroundReplenishment(poolOptions.backgroundReplenishment())
                    .open();
        } catch (PooledProtocolSessionException error) {
            throw MystemProtocolFailureMapper.map(error);
        } catch (ProtocolSessionException error) {
            throw MystemProtocolFailureMapper.map(error);
        } catch (RuntimeException error) {
            throw new MystemStartupException("Failed to start pooled MyStem session.", error);
        }
        return new PooledMystemClient(pool, oneShotClient, options, requestTimeout);
    }

    private void validateRuntimeOptions() {
        options.fixlist().ifPresent(path -> {
            if (!Files.isReadable(path)) {
                throw new MystemInvalidOptionsException("fixlist must be readable: " + path);
            }
        });
    }

    private OneShotMystemClient newOneShotClient(Path resolvedExecutable) {
        return new OneShotMystemClient(
                resolvedExecutable,
                options,
                requestTimeout,
                maxRequestChars,
                maxRequestBytes,
                maxResponseBytes,
                includeInputInDiagnostics);
    }
}
