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
 * <p>The default mode is one-shot with JSON output and UTF-8 encoding. Call {@link #session()} for one
 * long-lived JSON-line process or {@link #pooled(Consumer)} for a thread-safe pool of JSON-line processes.
 * Session and pool modes require JSON, reject {@code newLineEachWord(true)}, and accept text requests
 * without CR or LF only. File requests always run in separate one-shot processes.
 *
 * <p>Defaults: request timeout three seconds, idle timeout disabled, request limits 1,000,000 UTF-16
 * code units and 4,000,000 encoded bytes, response limits 8,000,000 UTF-16 code units and 32,000,000 bytes.
 * PATH lookup is enabled and full input in diagnostics is disabled. This mutable builder is not intended
 * for concurrent configuration. Close every built client when it is no longer needed.
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
     * Sets the executable path, taking precedence over the system property, environment and PATH.
     *
     * <p>The path must identify a regular executable file when {@link #build()} is called.
     *
     * @param executable executable path
     * @return this builder
     */
    public MystemClientBuilder executable(Path executable) {
        this.executable = Optional.of(Objects.requireNonNull(executable, "executable"));
        return this;
    }

    /**
     * Sets MyStem CLI options; defaults are JSON, UTF-8 and all boolean flags disabled.
     *
     * @param options options
     * @return this builder
     */
    public MystemClientBuilder options(MystemOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        return this;
    }

    /**
     * Enables or disables PATH lookup; enabled by default.
     *
     * <p>Without an explicit path, resolution first checks the {@code mystem4j.executable} system property,
     * then {@code MYSTEM_PATH}, then PATH if enabled. Disabling PATH lookup does not disable the other sources.
     *
     * @param searchPath whether PATH lookup is enabled
     * @return this builder
     */
    public MystemClientBuilder searchPath(boolean searchPath) {
        this.searchPath = searchPath;
        return this;
    }

    /**
     * Sets the execution timeout; defaults to three seconds.
     *
     * <p>For pooled requests this timeout starts when the acquired worker executes the request and excludes
     * admission/acquisition waits. Session calls waiting behind another caller also wait outside this timeout.
     * For total request timing, see {@link MystemRequestStats}.
     *
     * @param requestTimeout positive duration
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
     * Sets the process I/O inactivity timeout for reusable and pooled workers; disabled by default.
     *
     * <p>The timeout tracks stdin/stdout/stderr activity, including during a request. A long-running silent
     * request can therefore lose its worker before {@link #requestTimeout(Duration)} expires. A reusable
     * client whose process has stopped must be closed and replaced; pools replace failed workers. One-shot
     * requests, including file requests, do not use this timeout.
     *
     * @param idleTimeout non-negative duration, or {@link Duration#ZERO} (the default) to disable
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
     * Selects reusable JSON-line session mode; concurrent requests are serialized.
     *
     * <p>Requires JSON output and {@code newLineEachWord(false)}. Text requests must not contain CR or LF.
     *
     * @return this builder
     */
    public MystemClientBuilder session() {
        this.mode = Mode.SESSION;
        return this;
    }

    /**
     * Selects pooled JSON-line session mode and configures a fresh pool options builder.
     *
     * <p>Requires JSON output and {@code newLineEachWord(false)}. Text requests must not contain CR or LF.
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
     * <p>Requires JSON output and {@code newLineEachWord(false)}. Text requests must not contain CR or LF.
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
     * Selects pooled JSON-line session mode, preserving any pool options previously configured on this builder.
     *
     * <p>On a fresh builder, uses {@link MystemPoolOptions#builder()} defaults. Requires JSON output and
     * {@code newLineEachWord(false)}. Text requests must not contain CR or LF.
     *
     * @return this builder
     */
    public MystemClientBuilder pooled() {
        this.mode = Mode.POOL;
        return this;
    }

    /**
     * Sets the maximum input UTF-16 code units for a text request; defaults to 1,000,000.
     *
     * <p>Applies to {@link String#length()}, not code points, and excludes the session/pool protocol newline.
     * Exactly the limit is accepted. Does not limit input files.
     *
     * @param maxRequestChars positive payload limit in UTF-16 code units
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
     * Sets the maximum encoded input bytes for a text request; defaults to 4,000,000.
     *
     * <p>Uses {@link MystemOptions#encoding()} and excludes the session/pool protocol newline. Exactly the
     * limit is accepted. Does not limit input files.
     *
     * @param maxRequestBytes positive encoded payload limit in bytes
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
     * Sets the maximum decoded stdout UTF-16 code units; defaults to 8,000,000.
     *
     * <p>Applies to captured responses in every mode, including line endings. Exactly the limit is accepted.
     * Does not limit output written directly to a file.
     *
     * @param maxResponseChars positive decoded stdout limit in UTF-16 code units
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
     * Sets the captured output and protocol buffer bound in bytes; defaults to 32,000,000.
     *
     * <p>One-shot requests bound stdout and stderr capture. Session/pool workers also use this bound for
     * encoded responses and output backlog. Line endings count toward the limit. This is not a limit on
     * output written directly to a file or a total process-memory budget.
     *
     * @param maxResponseBytes positive output byte bound
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
     * Controls whether one-shot process-failure messages may include full input; disabled by default.
     *
     * <p>This also affects file-path diagnostics for one-shot file requests in any mode. It does not redact
     * text independently printed by MyStem to stderr.
     *
     * @param includeInputInDiagnostics whether input may be included
     * @return this builder
     */
    public MystemClientBuilder includeInputInDiagnostics(boolean includeInputInDiagnostics) {
        this.includeInputInDiagnostics = includeInputInDiagnostics;
        return this;
    }

    /**
     * Builds a configured client and resolves its executable.
     *
     * <p>One-shot mode starts a process on each request. Session mode starts its process now; pool mode
     * performs configured warmup now and otherwise creates workers as needed. No download or license
     * acceptance is performed. The caller owns the returned client and must close it.
     *
     * @return client
     * @throws MystemExecutableNotFoundException when no executable can be resolved
     * @throws MystemInvalidOptionsException when the mode is incompatible with configured options or a fixlist
     *     is not a readable regular file
     * @throws MystemStartupException when a reusable session or pool cannot be started
     * @throws MystemException when session or pool startup fails with a protocol-level runtime error
     */
    public MystemClient build() {
        validateRuntimeOptions();
        Path resolvedExecutable = MystemExecutableResolver.resolve(executable, searchPath);
        MystemRequestLimits requestLimits = new MystemRequestLimits(maxRequestChars, maxRequestBytes, options.encoding().charset());
        OneShotMystemClient oneShotClient = newOneShotClient(resolvedExecutable, requestLimits);
        if (mode == Mode.ONE_SHOT) {
            return oneShotClient;
        }
        if (mode == Mode.SESSION) {
            ProtocolSession<String, String> session;
            try {
                session = Icli.command(resolvedExecutable.toString())
                        .protocolSession(() -> new JsonLineMystemAdapter(maxResponseChars))
                        .withArgs(options.toArguments())
                        .withRequestTimeout(requestTimeout)
                        .withIdleTimeout(idleTimeout)
                        .withMaxRequestChars(requestLimits.framedChars())
                        .withMaxRequestBytes(requestLimits.framedBytes())
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
            return new ReusableMystemClient(session, oneShotClient, options, requestTimeout, requestLimits);
        }

        PooledProtocolSession<String, String> pool;
        try {
            pool = Icli.command(resolvedExecutable.toString())
                    .protocolSession(() -> new JsonLineMystemAdapter(maxResponseChars))
                    .withArgs(options.toArguments())
                    .withRequestTimeout(requestTimeout)
                    .withIdleTimeout(idleTimeout)
                    .withMaxRequestChars(requestLimits.framedChars())
                    .withMaxRequestBytes(requestLimits.framedBytes())
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
        return new PooledMystemClient(pool, oneShotClient, options, requestTimeout, requestLimits, poolOptions);
    }

    private void validateRuntimeOptions() {
        if (mode != Mode.ONE_SHOT) {
            if (options.format() != MystemOutputFormat.JSON) {
                throw new MystemInvalidOptionsException("Reusable and pooled MyStem clients require JSON format.");
            }
            if (options.newLineEachWord()) {
                throw new MystemInvalidOptionsException(
                        "Reusable and pooled MyStem clients cannot use newLineEachWord because it breaks JSON-line request framing.");
            }
        }
        options.fixlist().ifPresent(path -> {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new MystemInvalidOptionsException("fixlist must be a readable regular file: " + path);
            }
        });
    }

    private OneShotMystemClient newOneShotClient(Path resolvedExecutable, MystemRequestLimits requestLimits) {
        return new OneShotMystemClient(
                resolvedExecutable,
                options,
                requestTimeout,
                requestLimits,
                maxResponseChars,
                maxResponseBytes,
                includeInputInDiagnostics);
    }
}
