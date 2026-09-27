package io.github.ulviar.mystem4j.server;

import com.fasterxml.jackson.core.JacksonException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnectionLimit;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.util.Callback;

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
 * <p>Each instance accepts up to 256 connections, with a 60-second connection idle timeout and
 * a 16 KiB request-header limit. These are instance settings, not JVM system properties. The idle
 * timeout does not bound the total time to receive headers; configure that at a public reverse proxy.
 *
 * <p>Admission is bounded with immediate rejection; HTTP deadlines include body reads and writes.
 * Deadlines start after headers arrive, interrupt the handler and close its connection. Built-in
 * backends respond to interruption; custom backends must also do so for bounded shutdown. A disconnected
 * caller may leave native work running until the server/native deadline. Jetty Core serves HTTP/1.1;
 * use a reverse proxy for TLS and public traffic policies.
 */
public final class MystemHttpServer implements AutoCloseable {
    private final MystemClient backend;
    private final Server server;
    private final InetSocketAddress address;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final Semaphore admission;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final MystemOutputFormat format;
    private final MystemClientExecutionProfile profile;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final Duration timeout;
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
        temporaryDirectory = builder.temporaryDirectory;
        authorization = builder.token == null ? null : ("Bearer " + builder.token).getBytes(StandardCharsets.US_ASCII);
        server = new Server();
        var http = new HttpConfiguration();
        http.setSendServerVersion(false);
        http.setSendXPoweredBy(false);
        http.setRequestHeaderSize(16 * 1024);
        var connector = new ServerConnector(server, 1, 1, new HttpConnectionFactory(http));
        connector.setHost(builder.address.getAddress().getHostAddress());
        connector.setPort(builder.address.getPort());
        connector.setIdleTimeout(60_000);
        server.addConnector(connector);
        server.addBean(new NetworkConnectionLimit(256, connector));
        server.setStopTimeout(TimeUnit.SECONDS.toMillis(builder.shutdownSeconds));
        server.setErrorHandler((request, response, callback) -> {
            // Container errors must not expose a URI, stack trace or backend diagnostic.
            response.getHeaders().put("Cache-Control", "no-store");
            response.write(true, null, callback);
            return true;
        });
        server.setHandler(new GracefulHandler(new Handler.Abstract.NonBlocking() {
            @Override public boolean handle(Request request, Response response, Callback callback) {
                accept(request, response, callback);
                return true;
            }
        }) {
            @Override protected void handleShutdownRejection(Request request, Response response, Callback callback) {
                error(response, 503, "CLOSED");
                callback.succeeded();
            }
        });
        try {
            timer.setRemoveOnCancelPolicy(true);
            server.start();
            address = new InetSocketAddress(builder.address.getAddress(), connector.getLocalPort());
        } catch (Exception failure) {
            try { server.stop(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            executor.shutdownNow();
            timer.shutdownNow();
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Could not start HTTP service", failure);
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
    public InetSocketAddress address() { return address; }

    private void accept(Request request, Response response, Callback callback) {
        if (closed.get()) {
            error(response, 503, "CLOSED");
            callback.succeeded();
        } else if (!admission.tryAcquire()) {
            error(response, 429, "BUSY");
            callback.succeeded();
        } else {
            var execution = new Execution(request, response, callback);
            try {
                request.addFailureListener(execution::abort);
                execution.deadline = timer.schedule(() -> execution.abort(
                        new java.util.concurrent.TimeoutException("HTTP request deadline exceeded")),
                        timeout.toNanos(), TimeUnit.NANOSECONDS);
                executor.execute(execution);
            } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
                execution.finish();
                error(response, 503, "CLOSED");
                callback.succeeded();
            }
        }
    }

    /** One admitted operation; callbacks never perform blocking backend work on Jetty I/O threads. */
    private final class Execution implements Runnable {
        private final Request request;
        private final Response response;
        private final Callback callback;
        private java.util.concurrent.ScheduledFuture<?> deadline;
        private Thread worker;
        private Throwable failure;
        private boolean finished;

        private Execution(Request request, Response response, Callback callback) {
            this.request = request;
            this.response = response;
            this.callback = callback;
        }

        private void abort(Throwable cause) {
            synchronized (this) {
                if (finished || failure != null) return;
                failure = cause;
                if (worker != null) worker.interrupt();
            }
            // Closing may invoke Jetty listeners. Do not hold our monitor across container callbacks.
            // Once failure is recorded, completion cannot succeed and reuse this HTTP/1.1 connection.
            request.getConnectionMetaData().getConnection().getEndPoint().close(cause);
        }

        private synchronized Throwable finish() {
            finished = true;
            worker = null;
            if (deadline != null) deadline.cancel(false);
            admission.release();
            return failure;
        }

        @Override public void run() {
            try {
                synchronized (this) {
                    worker = Thread.currentThread();
                    if (failure != null) worker.interrupt();
                }
                if (!Thread.currentThread().isInterrupted()) dispatch(request, response);
            } catch (Throwable cause) {
                if (cause instanceof Exception && !response.isCommitted() && !Thread.currentThread().isInterrupted()) {
                    reportFailure(response, cause);
                } else {
                    abort(cause);
                }
            } finally {
                Throwable cause = finish();
                if (cause == null) callback.succeeded();
                else callback.failed(cause);
            }
        }
    }

    private static void reportFailure(Response response, Throwable failure) {
        if (failure instanceof MystemInvalidOptionsException || failure instanceof JacksonException
                || failure instanceof NumberFormatException) error(response, 400, "INVALID_REQUEST");
        else if (failure instanceof MystemClosedException) error(response, 503, "CLOSED");
        else if (failure instanceof MystemRequestTimeoutException) error(response, 504, "TIMEOUT");
        else if (failure instanceof MystemPoolExhaustedException) error(response, 429, "BUSY");
        else if (failure instanceof MystemOutputLimitException) error(response, 413, "OUTPUT_LIMIT");
        else if (failure instanceof MystemStartupException) error(response, 502, "STARTUP");
        else if (failure instanceof MystemProtocolException) error(response, 502, "PROTOCOL");
        else if (failure instanceof MystemProcessException process) {
            process.exitCode().ifPresent(code -> response.getHeaders().put("X-Mystem-Exit-Code", Integer.toString(code)));
            error(response, 502, "PROCESS");
        } else error(response, 500, "INTERNAL");
    }

    private void dispatch(Request request, Response response) throws IOException {
        response.getHeaders().put("X-Mystem-Version", "1");
        response.getHeaders().put("Cache-Control", "no-store");
        if (closed.get()) { error(response, 503, "CLOSED"); return; }
        String path = request.getHttpURI().getPath();
        if ("/health/live".equals(path) && request.getHttpURI().getQuery() == null
                && "GET".equals(request.getMethod())) {
            response.setStatus(204);
            return;
        }
        if (!authorized(request)) { error(response, 401, "UNAUTHORIZED"); return; }
        if (request.getHttpURI().getQuery() != null || !java.util.Set.of(
                "/v1/info", "/v1/analyze", "/v1/files/content", "/v1/files/output").contains(path)) {
            error(response, 404, "NOT_FOUND"); return;
        }
        String method = "/v1/info".equals(path) ? "GET" : "POST";
        if (!method.equals(request.getMethod())) {
            response.getHeaders().put("Allow", method);
            error(response, 405, "METHOD_NOT_ALLOWED"); return;
        }
        if ("/v1/info".equals(path)) {
            response.getHeaders().put("X-Mystem-Format", format.name());
            response.getHeaders().put("X-Mystem-Profile", profile.name());
            response.setStatus(204);
            return;
        }
        String expectedType = path.equals("/v1/analyze") ? "application/json" : "application/octet-stream";
        String contentType = request.getHeaders().get("Content-Type");
        if (contentType == null || !contentType.equalsIgnoreCase(expectedType)
                || request.getHeaders().contains("Content-Encoding")) {
            error(response, 415, "UNSUPPORTED_MEDIA_TYPE"); return;
        }
        String length = request.getHeaders().get("Content-Length");
        if (length != null && Long.parseLong(length) > maxRequestBytes) {
            error(response, 413, "INVALID_REQUEST"); return;
        }
        var bodySource = new Request.Wrapper(request) {
            @Override public void fail(Throwable failure) {
                // InputStream.close() releases unread chunks but would otherwise fail the exchange
                // before we can send INVALID_REQUEST. Let Jetty close it after the error response.
                response.getHeaders().put("Connection", "close");
            }
        };
        try (var input = ServerWire.bounded(Content.Source.asInputStream(bodySource), maxRequestBytes)) {
            if (path.equals("/v1/analyze")) {
                var result = backend.analyze(ServerWire.text(input, maxRequestBytes));
                sendText(response, result.output(), result.format(), result.stats());
            } else {
                file(response, input, path.endsWith("/output"));
            }
        }
    }

    private boolean authorized(Request request) {
        if (authorization == null) return true;
        var values = request.getHeaders().getValuesList("Authorization");
        return values != null && values.size() == 1 && MessageDigest.isEqual(authorization,
                values.getFirst().getBytes(StandardCharsets.UTF_8));
    }

    private void file(Response response, java.io.InputStream input, boolean direct) throws IOException {
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
                ServerWire.stats(response.getHeaders(), result.format(), result.stats());
                response.getHeaders().put("Content-Type", "application/octet-stream");
                response.setStatus(200);
                response.getHeaders().put("Content-Length", Long.toString(size));
                try (var output = Content.Sink.asOutputStream(response)) { Files.copy(target, output); }
            } else {
                var result = backend.analyzeFile(source);
                sendText(response, result.output(), result.format(), result.stats());
            }
        } finally {
            try { Files.deleteIfExists(target); }
            finally {
                try { Files.deleteIfExists(source); }
                finally { Files.deleteIfExists(directory); }
            }
        }
    }

    private void sendText(Response response, String output, MystemOutputFormat resultFormat, MystemRequestStats stats)
            throws IOException {
        byte[] bytes = ServerWire.output(output, maxResponseBytes);
        ServerWire.stats(response.getHeaders(), resultFormat, stats);
        response.getHeaders().put("Content-Type", "application/json");
        response.setStatus(200);
        response.getHeaders().put("Content-Length", Integer.toString(bytes.length));
        try (var body = Content.Sink.asOutputStream(response)) { body.write(bytes); }
    }

    private static void error(Response response, int status, String code) {
        response.getHeaders().put("X-Mystem-Error", code);
        response.getHeaders().put("X-Mystem-Version", "1");
        response.getHeaders().put("Cache-Control", "no-store");
        response.setStatus(status);
        response.getHeaders().put("Content-Length", "0");
    }

    /**
     * Stops admission, drains exchanges for the configured grace period, interrupts remaining handlers,
     * and closes the owned backend. Idempotent; the service cannot restart.
     * @throws MystemException if transport or backend cleanup fails
     */
    @Override public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                server.stop();
            } catch (java.util.concurrent.TimeoutException expired) {
                // Jetty has closed the connectors after the drain grace expired.
                if (expired.getSuppressed().length != 0) throw new MystemException("HTTP shutdown failed", expired);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new MystemException("HTTP shutdown failed", failure);
            } finally {
                executor.shutdownNow();
                timer.shutdownNow();
                try { executor.close(); }
                finally { backend.close(); }
            }
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
         * Sets the total handler deadline after headers arrive, including upload, backend and download.
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
         * @throws IOException if transport startup fails
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
