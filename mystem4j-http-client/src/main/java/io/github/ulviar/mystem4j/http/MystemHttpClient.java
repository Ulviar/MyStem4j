package io.github.ulviar.mystem4j.http;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemClientExecutionProfile;
import io.github.ulviar.mystem4j.MystemClosedException;
import io.github.ulviar.mystem4j.MystemException;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemProtocolException;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.MystemRequestTimeoutException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe {@link MystemClient} that sends requests to a MyStem4j HTTP service.
 *
 * <pre>{@code
 * try (var client = MystemHttpClient.builder(URI.create("http://localhost:8080/")).build()) {
 *     System.out.println(client.analyze("Кошки спят.").output());
 * }
 * }</pre>
 *
 * <p>Construction fetches server metadata. The server chooses native options and execution mode;
 * single-line restrictions still apply to session/pool text requests. Strings cross the wire as JSON
 * without Unicode normalization. File methods upload bytes in the server's native input encoding.
 * Returned paths always refer to caller-owned local files. Statistics describe server execution and
 * exclude HTTP transfer time. No MyStem process is started on the client machine.
 *
 * <p>HTTP deadlines cover upload, server waiting/execution and the complete response body. A timeout,
 * interruption or disconnect does not prove that remote work stopped. Requests are not resubmitted by
 * this library. Metadata stays cached after closure; configure a service endpoint with consistent
 * options when placing several servers behind a load balancer.
 */
public final class MystemHttpClient implements MystemClient {
    private final URI endpoint;
    private final Duration timeout;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final String token;
    private final HttpClient http;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final MystemOutputFormat format;
    private final MystemClientExecutionProfile profile;

    private MystemHttpClient(Builder builder) {
        endpoint = builder.endpoint;
        timeout = builder.timeout;
        maxRequestBytes = builder.maxRequestBytes;
        maxResponseBytes = builder.maxResponseBytes;
        token = builder.token;
        http = HttpClient.newBuilder().connectTimeout(builder.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            var response = exchange("info", null, null, null, 204);
            format = MystemOutputFormat.valueOf(ClientWire.header(response.headers(), "Format"));
            profile = MystemClientExecutionProfile.valueOf(ClientWire.header(response.headers(), "Profile"));
        } catch (RuntimeException failure) {
            close();
            throw failure instanceof MystemException ? failure
                    : new MystemProtocolException("Invalid MyStem service metadata", failure);
        }
    }

    /**
     * Creates a builder for a service root URI, optionally including a reverse-proxy path prefix.
     * @param endpoint absolute HTTP(S) URI without user information, query or fragment
     * @return a new mutable builder; no network request has occurred
     * @throws IllegalArgumentException if the URI is not an HTTP(S) service root
     * @throws NullPointerException if endpoint is null
     */
    public static Builder builder(URI endpoint) { return new Builder(endpoint); }

    /** {@inheritDoc} The value is the cached server profile, not an HTTP connection-pool description. */
    @Override public MystemClientExecutionProfile executionProfile() { return profile; }

    /** {@inheritDoc} The format is fetched during construction and checked on every successful response. */
    @Override public Optional<MystemOutputFormat> outputFormat() { return Optional.of(format); }

    /**
     * {@inheritDoc}
     * <p>The HTTP byte limit applies to the encoded JSON request and response, including escape sequences.
     */
    @Override
    public MystemRawResult analyze(String text) {
        ensureOpen();
        Objects.requireNonNull(text, "text");
        if (text.length() > maxRequestBytes) throw new MystemInvalidOptionsException("HTTP request is too large");
        try {
            byte[] bytes = ClientWire.text(text);
            if (bytes.length > maxRequestBytes) throw new MystemInvalidOptionsException("HTTP request is too large");
            var response = exchange("analyze", HttpRequest.BodyPublishers.ofByteArray(bytes), "application/json", null, 200);
            return new MystemRawResult(text, decode(response), responseFormat(response), ClientWire.stats(response.headers()));
        } catch (IOException | IllegalArgumentException failure) {
            throw new MystemProtocolException("Invalid MyStem HTTP response", failure);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Uploads the input bytes and captures the returned string within the configured HTTP response limit.
     * The server uses a private temporary file and its backend's file-request semantics.
     */
    @Override
    public MystemFileContentResult analyzeFile(Path input) {
        ensureOpen();
        validateInput(input);
        try {
            var response = exchange("files/content", HttpRequest.BodyPublishers.ofFile(input),
                    "application/octet-stream", null, 200);
            return new MystemFileContentResult(input, decode(response), responseFormat(response), ClientWire.stats(response.headers()));
        } catch (IOException | IllegalArgumentException failure) {
            throw new MystemProtocolException("File transfer or HTTP response validation failed", failure);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Streams upload/download bytes with bounded memory. A temporary download in the output directory is
     * replaced into {@code output} only after successful transfer and metadata validation. Failure preserves
     * an existing output file; replacing it requires an atomic move supported by that filesystem. The
     * transfer limit applies even though the native runtime does not bound direct file output.
     */
    @Override
    public MystemFileResult analyzeFile(Path input, Path output) {
        ensureOpen();
        validateInput(input);
        Objects.requireNonNull(output, "output");
        Path temporary = null;
        try {
            Path target = output.toAbsolutePath().normalize();
            if (input.toAbsolutePath().normalize().equals(target)
                    || (Files.exists(target) && Files.isSameFile(input, target))
                    || !Files.isDirectory(target.getParent()) || Files.isDirectory(target)) {
                throw new MystemInvalidOptionsException("Output must be a different file in an existing directory");
            }
            temporary = Files.createTempFile(target.getParent(), ".mystem-download-", ".tmp");
            var response = exchange("files/output", HttpRequest.BodyPublishers.ofFile(input),
                    "application/octet-stream", temporary, 200);
            var result = new MystemFileResult(input, output, responseFormat(response), ClientWire.stats(response.headers()));
            ClientWire.requireMediaType(response.headers(), "application/octet-stream");
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return result;
        } catch (IOException | IllegalArgumentException failure) {
            throw new MystemProtocolException("File transfer or output replacement failed", failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException failure) { throw new MystemException("Could not remove temporary download", failure); }
            }
        }
    }

    private void validateInput(Path input) {
        Objects.requireNonNull(input, "input");
        try {
            if (!Files.isRegularFile(input) || !Files.isReadable(input) || Files.size(input) > maxRequestBytes) {
                throw new MystemInvalidOptionsException("Input must be a readable regular file within the HTTP byte limit");
            }
        } catch (IOException failure) { throw new MystemInvalidOptionsException("Cannot inspect input file"); }
    }

    private String decode(HttpResponse<byte[]> response) throws IOException {
        ClientWire.requireMediaType(response.headers(), "application/json");
        return ClientWire.output(response.body(), maxResponseBytes);
    }

    private MystemOutputFormat responseFormat(HttpResponse<?> response) {
        var actual = MystemOutputFormat.valueOf(ClientWire.header(response.headers(), "Format"));
        if (actual != format) throw new MystemProtocolException("Service output format changed; recreate client", null);
        return actual;
    }

    private HttpResponse<byte[]> exchange(String path, HttpRequest.BodyPublisher body, String type, Path file, int expected) {
        ensureOpen();
        var request = HttpRequest.newBuilder(endpoint.resolve("v1/" + path)).timeout(timeout);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.GET();
        else request.header("Content-Type", type).POST(body);
        try (var receiver = new HttpResponseBody(maxResponseBytes, file)) {
            var future = http.sendAsync(request.build(), receiver);
            pending.add(future);
            if (closed.get()) future.cancel(true);
            try {
                var response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                if (response.statusCode() != expected) throw ClientWire.error(response.statusCode(), response.headers());
                if (!"1".equals(ClientWire.header(response.headers(), "Version"))) {
                    throw new MystemProtocolException("Unsupported MyStem HTTP protocol version", null);
                }
                return response;
            } catch (TimeoutException failure) {
                throw new MystemRequestTimeoutException("MyStem HTTP deadline exceeded");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new MystemProtocolException("MyStem HTTP request interrupted", failure);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (closed.get()) throw new MystemClosedException("MyStem HTTP request cancelled by close");
                if (cause instanceof MystemException mystem) throw mystem;
                if (cause instanceof java.net.http.HttpTimeoutException) {
                    throw new MystemRequestTimeoutException("MyStem HTTP deadline exceeded");
                }
                throw new MystemProtocolException("MyStem HTTP transport failed", cause);
            } catch (java.util.concurrent.CancellationException failure) {
                throw new MystemClosedException("MyStem HTTP request cancelled by close");
            } finally {
                future.cancel(true);
                pending.remove(future);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new MystemClosedException("MyStem HTTP client is closed");
    }

    /**
     * Cancels this client's in-flight HTTP requests and shuts down its connection resources, idempotently.
     * <p>Does not shut down the remote service or guarantee cancellation of remote native execution.
     * Subsequent request methods throw {@link MystemClosedException}; metadata remains available.
     */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            pending.forEach(future -> future.cancel(true));
            http.shutdownNow();
        }
    }

    /** Mutable, non-thread-safe connection configuration. Build creates a separate owning client. */
    public static final class Builder {
        private final URI endpoint;
        private Duration timeout = Duration.ofSeconds(60);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private int maxRequestBytes = 8 * 1024 * 1024;
        private int maxResponseBytes = 64 * 1024 * 1024;
        private String token;

        private Builder(URI value) {
            Objects.requireNonNull(value, "endpoint");
            if (!("http".equals(value.getScheme()) || "https".equals(value.getScheme())) || value.getHost() == null
                    || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null) {
                throw new IllegalArgumentException("Expected an absolute HTTP(S) service root URI");
            }
            endpoint = URI.create(value.toString().endsWith("/") ? value.toString() : value + "/");
        }

        /**
         * Sets the complete HTTP request deadline, including the response body; default 60 seconds.
         * @param value positive duration, at most one day
         * @return this builder
         * @throws IllegalArgumentException if outside the allowed range
         */
        public Builder requestTimeout(Duration value) { timeout = duration(value); return this; }

        /**
         * Sets the connection-establishment timeout; default 5 seconds.
         * @param value positive duration, at most one day
         * @return this builder
         * @throws IllegalArgumentException if outside the allowed range
         */
        public Builder connectTimeout(Duration value) { connectTimeout = duration(value); return this; }

        /**
         * Sets the JSON/upload body byte limit; default 8 MiB. JSON escaping counts toward this limit.
         * @param value positive byte count
         * @return this builder
         * @throws IllegalArgumentException if non-positive
         */
        public Builder maxRequestBytes(int value) { maxRequestBytes = positive(value); return this; }

        /**
         * Sets the JSON/download body byte limit; default 64 MiB. File downloads stream to disk.
         * @param value positive byte count
         * @return this builder
         * @throws IllegalArgumentException if non-positive
         */
        public Builder maxResponseBytes(int value) { maxResponseBytes = positive(value); return this; }

        /**
         * Sets a bearer token for every request, including the metadata handshake. Use HTTPS outside a trusted network.
         * @param value nonempty printable ASCII token without spaces
         * @return this builder
         * @throws IllegalArgumentException if the token contains whitespace, control or non-ASCII characters
         * @throws NullPointerException if value is null
         */
        public Builder bearerToken(String value) {
            Objects.requireNonNull(value, "token");
            if (value.isEmpty() || value.chars().anyMatch(c -> c <= 32 || c >= 127)) {
                throw new IllegalArgumentException("Expected a nonempty printable ASCII token without spaces");
            }
            token = value;
            return this;
        }

        /**
         * Connects to the service and checks protocol metadata before returning the owning client.
         * @return a new client; close it after use
         * @throws MystemException if connection, authentication, deadline or protocol validation fails
         */
        public MystemHttpClient build() { return new MystemHttpClient(this); }

        private static Duration duration(Duration value) {
            if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("Duration must be positive and at most one day");
            }
            return value;
        }

        private static int positive(int value) {
            if (value <= 0) throw new IllegalArgumentException("Byte limit must be positive");
            return value;
        }
    }
}
