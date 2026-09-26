package io.github.ulviar.mystem4j.server;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemException;
import io.github.ulviar.mystem4j.MystemOptions;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Standalone service launcher, configured through environment variables.
 *
 * <p>Set {@code MYSTEM_EXECUTABLE} to an installed native binary. Defaults are pooled JSON/UTF-8,
 * grammar information, disambiguation and copied input, listening on 127.0.0.1:8080. Run with
 * {@code --help} for configuration names. The process owns its service and closes it on JVM shutdown.
 * It does not install MyStem or accept a license on the caller's behalf.
 */
public final class MystemServerMain {
    private MystemServerMain() {}

    /**
     * Starts the service until JVM shutdown, or prints help.
     * @param args empty, or a single {@code --help} argument
     * @throws IOException if token-file reading or socket binding fails
     * @throws InterruptedException if the main thread is interrupted
     * @throws IllegalArgumentException if configuration is invalid
     * @throws MystemException if native backend creation or shutdown fails
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println(HELP);
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("Use --help or configure environment variables");
        // JDK HTTP server controls act before a handler exists. Set defaults only for this standalone process.
        System.setProperty("jdk.httpserver.maxConnections", System.getProperty("jdk.httpserver.maxConnections", "256"));
        System.setProperty("sun.net.httpserver.maxReqTime", System.getProperty("sun.net.httpserver.maxReqTime", "60"));
        System.setProperty("sun.net.httpserver.maxRspTime", System.getProperty("sun.net.httpserver.maxRspTime", "60"));
        var server = start(System.getenv());
        Thread hook = new Thread(server::close, "mystem-service-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            System.out.println("MyStem HTTP service listening on " + server.address());
            new CountDownLatch(1).await();
        } finally {
            server.close();
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException shutdownInProgress) { /* JVM is already running the hook. */ }
        }
    }

    static MystemHttpServer start(Map<String, String> env) throws IOException {
        String executable = env.get("MYSTEM_EXECUTABLE");
        if (executable == null || executable.isBlank()) throw new IllegalArgumentException("Set MYSTEM_EXECUTABLE");
        int requestBytes = number(env, "MYSTEM_HTTP_MAX_REQUEST_BYTES", 8 * 1024 * 1024);
        int responseBytes = number(env, "MYSTEM_HTTP_MAX_RESPONSE_BYTES", 64 * 1024 * 1024);
        String mode = env.getOrDefault("MYSTEM_MODE", "pooled");
        if (!java.util.Set.of("oneshot", "session", "pooled").contains(mode)) throw new IllegalArgumentException("MYSTEM_MODE must be oneshot, session or pooled");
        var nativeBuilder = Mystem.builder().executable(Path.of(executable))
                .options(MystemOptions.builder().copyInput(true).grammarInfo(true).disambiguate(true).build())
                .requestTimeout(Duration.ofMillis(number(env, "MYSTEM_NATIVE_TIMEOUT_MS", 30_000)))
                .maxRequestChars(requestBytes).maxRequestBytes(requestBytes)
                .maxResponseChars(responseBytes).maxResponseBytes(responseBytes);
        if (mode.equals("session")) nativeBuilder.session();
        if (mode.equals("pooled")) nativeBuilder.pooled(pool -> pool
                .maxSize(number(env, "MYSTEM_POOL_SIZE", 4))
                .acquireTimeout(Duration.ofMillis(number(env, "MYSTEM_POOL_ACQUIRE_TIMEOUT_MS", 2_000))));
        MystemClient backend = nativeBuilder.build();
        try {
            var builder = MystemHttpServer.builder(backend)
                    .address(new InetSocketAddress(env.getOrDefault("MYSTEM_HOST", "127.0.0.1"), number(env, "MYSTEM_PORT", 8080)))
                    .maxConcurrentRequests(number(env, "MYSTEM_HTTP_CONCURRENCY", 16))
                    .maxRequestBytes(requestBytes).maxResponseBytes(responseBytes)
                    .requestTimeout(Duration.ofMillis(number(env, "MYSTEM_HTTP_TIMEOUT_MS", 45_000)))
                    .shutdownGraceSeconds(number(env, "MYSTEM_SHUTDOWN_GRACE_SECONDS", 5));
            if (env.containsKey("MYSTEM_TEMP_DIRECTORY")) builder.temporaryDirectory(Path.of(env.get("MYSTEM_TEMP_DIRECTORY")));
            if (env.containsKey("MYSTEM_TOKEN_FILE")) {
                try (var input = Files.newInputStream(Path.of(env.get("MYSTEM_TOKEN_FILE")))) {
                    byte[] bytes = input.readNBytes(4097);
                    if (bytes.length > 4096) throw new IllegalArgumentException("Token file exceeds 4096 bytes");
                    builder.bearerToken(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip());
                }
            }
            return builder.start();
        } catch (IOException | RuntimeException failure) {
            try { backend.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static int number(Map<String, String> env, String key, int fallback) {
        try { return Integer.parseInt(env.getOrDefault(key, Integer.toString(fallback))); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Expected integer for " + key, failure); }
    }

    private static final String HELP = """
            MyStem4j HTTP server (Java 25+)
            Required: MYSTEM_EXECUTABLE=/absolute/path/to/mystem
            MYSTEM_MODE=pooled                  oneshot | session | pooled
            MYSTEM_HOST=127.0.0.1               bind address
            MYSTEM_PORT=8080                    bind port (0 allocates one)
            MYSTEM_POOL_SIZE=4                 native text workers
            MYSTEM_POOL_ACQUIRE_TIMEOUT_MS=2000
            MYSTEM_NATIVE_TIMEOUT_MS=30000
            MYSTEM_HTTP_TIMEOUT_MS=45000       full handler deadline
            MYSTEM_HTTP_CONCURRENCY=16         admitted exchanges, including files
            MYSTEM_HTTP_MAX_REQUEST_BYTES=8388608
            MYSTEM_HTTP_MAX_RESPONSE_BYTES=67108864
            MYSTEM_SHUTDOWN_GRACE_SECONDS=5
            MYSTEM_TEMP_DIRECTORY              existing writable directory (default JVM temp)
            MYSTEM_TOKEN_FILE                  UTF-8 bearer token file (optional, max 4096 bytes)
            No native download. Use a TLS reverse proxy outside a trusted network.
            GET /health/live; GET /v1/info; POST /v1/analyze; POST /v1/files/{content,output}
            """;
}
