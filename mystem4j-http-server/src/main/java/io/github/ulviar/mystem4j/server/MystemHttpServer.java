package io.github.ulviar.mystem4j.server;

import com.fasterxml.jackson.core.JacksonException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemClientExecutionProfile;
import io.github.ulviar.mystem4j.MystemClosedException;
import io.github.ulviar.mystem4j.MystemException;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemOutputLimitException;
import io.github.ulviar.mystem4j.MystemPoolExhaustedException;
import io.github.ulviar.mystem4j.MystemProcessException;
import io.github.ulviar.mystem4j.MystemProtocolException;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.MystemRequestTimeoutException;
import io.github.ulviar.mystem4j.MystemStartupException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP service that owns one configured {@link MystemClient} after successful startup.
 *
 * <pre>{@code
 * MystemClient backend = Mystem.builder().executable(Path.of("/opt/mystem/mystem")).pooled().build();
 * MystemHttpServer server;
 * try {
 *     server = MystemHttpServer.builder(backend).start();
 * } catch (Exception startupFailure) {
 *     backend.close();
 *     throw startupFailure;
 * }
 * // The application owns server and calls server.close() during shutdown.
 * }</pre>
 *
 * <p>The backend must support concurrent method calls. Built-in one-shot, session and pool clients do;
 * their process behavior and text restrictions remain unchanged. All file calls use private temporary
 * files and the backend's file methods. No client-supplied path or executable option is accepted.
 * Statistics are backend measurements, not HTTP timings. Backend exception diagnostics are never sent
 * over the network, because they may contain source text or server paths.
 *
 * <p>Admission is bounded with immediate rejection; HTTP deadlines include body reads and writes.
 * Deadlines interrupt the handler and close its exchange. Built-in backends respond to interruption;
 * custom backends must also do so for bounded shutdown. A disconnected caller may leave native work
 * running until the server/native deadline. Use a reverse proxy for TLS and public connection limits.
 */
public final class MystemHttpServer implements AutoCloseable {
    private final MystemClient backend;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final Semaphore admission;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final MystemOutputFormat format;
    private final MystemClientExecutionProfile profile;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final Duration timeout;
    private final int shutdownSeconds;
    private final Path temporaryDirectory;
    private final byte[] authorization;

    private MystemHttpServer(Builder builder) throws IOException {
        backend = builder.backend;
        format = backend.outputFormat().orElseThrow(() -> new IllegalArgumentException("Backend must expose its output format"));
        profile = Objects.requireNonNull(backend.executionProfile(), "executionProfile");
        admission = new Semaphore(builder.maxConcurrentRequests);
        maxRequestBytes = builder.maxRequestBytes;
        maxResponseBytes = builder.maxResponseBytes;
        timeout = builder.timeout;
        shutdownSeconds = builder.shutdownSeconds;
        temporaryDirectory = builder.temporaryDirectory;
        authorization = builder.token == null ? null : ("Bearer " + builder.token).getBytes(StandardCharsets.US_ASCII);
        server = HttpServer.create(builder.address, builder.maxConcurrentRequests);
        try {
            timer.setRemoveOnCancelPolicy(true);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        } catch (RuntimeException failure) {
            server.stop(0);
            executor.shutdownNow();
            timer.shutdownNow();
            throw failure;
        }
    }

    /**
     * Configures a service around an existing backend. Ownership transfers only when {@code start()} succeeds.
     * @param backend thread-safe backend with known output format
     * @return new mutable builder
     * @throws NullPointerException if backend is null
     */
    public static Builder builder(MystemClient backend) { return new Builder(backend); }

    /**
     * Returns the bound address, including the allocated port when port zero was requested.
     * @return bound address; remains available after close
     */
    public InetSocketAddress address() { return server.getAddress(); }

    private void handle(HttpExchange exchange) throws IOException {
        if (!admission.tryAcquire()) {
            try (exchange) { error(exchange, 429, "BUSY"); }
            return;
        }
        Thread handler = Thread.currentThread();
        java.util.concurrent.ScheduledFuture<?> deadline;
        try {
            deadline = timer.schedule(() -> {
                handler.interrupt();
                exchange.close();
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            admission.release();
            exchange.close();
            return;
        }
        try (exchange) {
            try {
                dispatch(exchange);
            } catch (Exception failure) {
                if (exchange.getResponseCode() == -1 && !handler.isInterrupted()) {
                    if (failure instanceof MystemInvalidOptionsException || failure instanceof JacksonException
                            || failure instanceof NumberFormatException) error(exchange, 400, "INVALID_REQUEST");
                    else if (failure instanceof MystemClosedException) error(exchange, 503, "CLOSED");
                    else if (failure instanceof MystemRequestTimeoutException) error(exchange, 504, "TIMEOUT");
                    else if (failure instanceof MystemPoolExhaustedException) error(exchange, 429, "BUSY");
                    else if (failure instanceof MystemOutputLimitException) error(exchange, 413, "OUTPUT_LIMIT");
                    else if (failure instanceof MystemStartupException) error(exchange, 502, "STARTUP");
                    else if (failure instanceof MystemProtocolException) error(exchange, 502, "PROTOCOL");
                    else if (failure instanceof MystemProcessException process) {
                        process.exitCode().ifPresent(code -> exchange.getResponseHeaders().set("X-Mystem-Exit-Code", Integer.toString(code)));
                        error(exchange, 502, "PROCESS");
                    } else error(exchange, 500, "INTERNAL");
                }
            }
        } finally {
            deadline.cancel(false);
            admission.release();
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("X-Mystem-Version", "1");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (closed.get()) { error(exchange, 503, "CLOSED"); return; }
        String path = exchange.getRequestURI().getRawPath();
        if ("/health/live".equals(path) && exchange.getRequestURI().getRawQuery() == null
                && "GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!authorized(exchange)) { error(exchange, 401, "UNAUTHORIZED"); return; }
        if (exchange.getRequestURI().getRawQuery() != null || !java.util.Set.of(
                "/v1/info", "/v1/analyze", "/v1/files/content", "/v1/files/output").contains(path)) {
            error(exchange, 404, "NOT_FOUND"); return;
        }
        String method = "/v1/info".equals(path) ? "GET" : "POST";
        if (!method.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", method);
            error(exchange, 405, "METHOD_NOT_ALLOWED"); return;
        }
        if ("/v1/info".equals(path)) {
            exchange.getResponseHeaders().set("X-Mystem-Format", format.name());
            exchange.getResponseHeaders().set("X-Mystem-Profile", profile.name());
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        String expectedType = path.equals("/v1/analyze") ? "application/json" : "application/octet-stream";
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.equalsIgnoreCase(expectedType)
                || exchange.getRequestHeaders().containsKey("Content-Encoding")) {
            error(exchange, 415, "UNSUPPORTED_MEDIA_TYPE"); return;
        }
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > maxRequestBytes) {
            error(exchange, 413, "INVALID_REQUEST"); return;
        }
        try (var input = ServerWire.bounded(exchange.getRequestBody(), maxRequestBytes)) {
            if (path.equals("/v1/analyze")) {
                var result = backend.analyze(ServerWire.text(input, maxRequestBytes));
                sendText(exchange, result.output(), result.format(), result.stats());
            } else {
                file(exchange, input, path.endsWith("/output"));
            }
        }
    }

    private boolean authorized(HttpExchange exchange) {
        if (authorization == null) return true;
        var values = exchange.getRequestHeaders().get("Authorization");
        return values != null && values.size() == 1 && MessageDigest.isEqual(authorization,
                values.getFirst().getBytes(StandardCharsets.UTF_8));
    }

    private void file(HttpExchange exchange, java.io.InputStream input, boolean direct) throws IOException {
        Path directory = temporaryDirectory == null ? Files.createTempDirectory("mystem-http-")
                : Files.createTempDirectory(temporaryDirectory, "mystem-http-");
        Path source = directory.resolve("input");
        Path target = directory.resolve("output");
        try {
            Files.copy(input, source);
            if (direct) {
                var result = backend.analyzeFile(source, target);
                long size = Files.size(target);
                if (size > maxResponseBytes) throw new MystemOutputLimitException("File response exceeds HTTP limit");
                ServerWire.stats(exchange.getResponseHeaders(), result.format(), result.stats());
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, size == 0 ? -1 : size);
                if (size != 0) try (var output = exchange.getResponseBody()) { Files.copy(target, output); }
            } else {
                var result = backend.analyzeFile(source);
                sendText(exchange, result.output(), result.format(), result.stats());
            }
        } finally {
            try { Files.deleteIfExists(target); }
            finally {
                try { Files.deleteIfExists(source); }
                finally { Files.deleteIfExists(directory); }
            }
        }
    }

    private void sendText(HttpExchange exchange, String output, MystemOutputFormat resultFormat, MystemRequestStats stats)
            throws IOException {
        byte[] bytes = ServerWire.output(output, maxResponseBytes);
        ServerWire.stats(exchange.getResponseHeaders(), resultFormat, stats);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void error(HttpExchange exchange, int status, String code) throws IOException {
        exchange.getResponseHeaders().set("X-Mystem-Error", code);
        exchange.getResponseHeaders().set("X-Mystem-Version", "1");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, -1);
    }

    /**
     * Stops admission, drains exchanges for the configured grace period, interrupts remaining handlers,
     * and closes the owned backend. Idempotent; the service cannot restart.
     * @throws MystemException if backend cleanup fails
     */
    @Override public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(shutdownSeconds);
            executor.shutdownNow();
            timer.shutdownNow();
            try { executor.close(); }
            finally { backend.close(); }
        }
    }

    /** Mutable, non-thread-safe server configuration. A backend may be transferred to only one server. */
    public static final class Builder {
        private final MystemClient backend;
        private InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
        private int maxConcurrentRequests = 16;
        private int maxRequestBytes = 8 * 1024 * 1024;
        private int maxResponseBytes = 64 * 1024 * 1024;
        private Duration timeout = Duration.ofSeconds(45);
        private int shutdownSeconds = 5;
        private Path temporaryDirectory;
        private String token;
        private boolean started;

        private Builder(MystemClient backend) { this.backend = Objects.requireNonNull(backend, "backend"); }

        /**
         * Sets the listening address; default 127.0.0.1:8080. Port zero allocates an ephemeral port.
         * @param value resolved socket address
         * @return this builder
         * @throws IllegalArgumentException if unresolved
         * @throws NullPointerException if null
         */
        public Builder address(InetSocketAddress value) {
            Objects.requireNonNull(value, "address");
            if (value.isUnresolved()) throw new IllegalArgumentException("Address must be resolved");
            address = value; return this;
        }

        /**
         * Sets the number of admitted exchanges; excess work receives 429 without entering the backend.
         * @param value positive count, default 16
         * @return this builder
         * @throws IllegalArgumentException if non-positive
         */
        public Builder maxConcurrentRequests(int value) { maxConcurrentRequests = positive(value); return this; }

        /**
         * Sets the body byte limit for JSON and binary uploads, including chunked bodies.
         * @param value positive count, default 8 MiB
         * @return this builder
         * @throws IllegalArgumentException if non-positive
         */
        public Builder maxRequestBytes(int value) { maxRequestBytes = positive(value); return this; }

        /**
         * Sets the JSON/download byte limit; native file output may occupy more disk before this check.
         * @param value positive count, default 64 MiB
         * @return this builder
         * @throws IllegalArgumentException if non-positive
         */
        public Builder maxResponseBytes(int value) { maxResponseBytes = positive(value); return this; }

        /**
         * Sets the total handler deadline, including upload, backend and download.
         * @param value positive duration at most one day, default 45 seconds
         * @return this builder
         * @throws IllegalArgumentException if outside the range
         */
        public Builder requestTimeout(Duration value) {
            if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("Timeout must be positive and at most one day");
            }
            timeout = value; return this;
        }

        /**
         * Sets the exchange-drain grace period before shutdown interrupts remaining handlers.
         * @param value zero to 86400 seconds, default 5
         * @return this builder
         * @throws IllegalArgumentException if outside the range
         */
        public Builder shutdownGraceSeconds(int value) {
            if (value < 0 || value > 86400) throw new IllegalArgumentException("Invalid shutdown grace period");
            shutdownSeconds = value; return this;
        }

        /**
         * Sets the existing parent directory for private per-request temporary directories.
         * @param value existing writable directory; default uses the JVM temporary directory
         * @return this builder
         * @throws IllegalArgumentException if not a writable directory
         * @throws NullPointerException if null
         */
        public Builder temporaryDirectory(Path value) {
            Objects.requireNonNull(value, "temporaryDirectory");
            if (!Files.isDirectory(value) || !Files.isWritable(value)) throw new IllegalArgumentException("Temporary directory must be writable");
            temporaryDirectory = value; return this;
        }

        /**
         * Requires this bearer token on all versioned endpoints; liveness stays unauthenticated.
         * @param value nonempty printable ASCII token without spaces
         * @return this builder
         * @throws IllegalArgumentException if malformed
         * @throws NullPointerException if null
         */
        public Builder bearerToken(String value) {
            Objects.requireNonNull(value, "token");
            if (value.isEmpty() || value.chars().anyMatch(c -> c <= 32 || c >= 127)) throw new IllegalArgumentException("Invalid bearer token");
            token = value; return this;
        }

        /**
         * Binds and starts the service, transferring backend ownership on success.
         * <p>On failure the caller must close the backend. This builder can start at most one server.
         * @return running service owned by the caller
         * @throws IOException if binding fails
         * @throws IllegalStateException if this builder already started a server
         * @throws IllegalArgumentException if the backend has no output-format metadata
         */
        public synchronized MystemHttpServer start() throws IOException {
            if (started) throw new IllegalStateException("Builder already started a server");
            var result = new MystemHttpServer(this);
            started = true;
            return result;
        }

        private static int positive(int value) {
            if (value <= 0) throw new IllegalArgumentException("Limit must be positive");
            return value;
        }
    }
}
