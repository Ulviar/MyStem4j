package io.github.ulviar.mystem4j.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.ulviar.mystem4j.*;
import io.github.ulviar.mystem4j.http.MystemHttpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpServiceContractTest {
    @TempDir Path directory;

    static URI uri(MystemHttpServer server) { return URI.create("http://127.0.0.1:" + server.address().getPort() + "/"); }
    MystemHttpServer.Builder service(MystemClient backend) {
        return MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .temporaryDirectory(directory).shutdownGraceSeconds(0);
    }

    @Test void unicodeAndStatsRoundTripWithoutNormalizingAnyUtf16CodeUnit() throws Exception {
        var backend = new Echo();
        try (var server = service(backend).start(); var client = MystemHttpClient.builder(uri(server)).build()) {
            assertEquals(MystemClientExecutionProfile.ONE_SHOT_PROCESS_PER_REQUEST, client.executionProfile());
            assertEquals(Optional.of(MystemOutputFormat.JSON), client.outputFormat());
            var texts = List.of("", "Кошки 🐈\r\n\t\u0000е\u0308", "\ud800x\udfff", "\ufeff\u2028\u2029");
            var results = client.analyzeAll(texts);
            for (int i = 0; i < texts.size(); i++) {
                assertEquals(texts.get(i), results.get(i).input());
                assertEquals(texts.get(i), results.get(i).output());
                assertEquals(texts.get(i).length(), results.get(i).stats().inputChars());
                assertEquals(Duration.ofMillis(7), results.get(i).stats().elapsed());
            }
        }
        assertEquals(1, backend.closes.get());
    }

    @Test void fileBytesPathsStatisticsAndCleanupArePreserved() throws Exception {
        Path input = directory.resolve("source.txt");
        Path output = directory.resolve("result.txt");
        Files.writeString(input, "Кошки\nсобаки\r\n🐈");
        Files.writeString(output, "old");
        try (var server = service(new Echo()).start(); var client = MystemHttpClient.builder(uri(server)).build()) {
            var capture = client.analyzeFile(input);
            assertEquals(input, capture.input());
            assertEquals(Files.readString(input), capture.output());
            var direct = client.analyzeFile(input, output);
            assertEquals(input, direct.input());
            assertEquals(output, direct.output());
            assertEquals(MystemExecutionMode.ONE_SHOT_FILE, direct.stats().mode());
            assertEquals(-1, direct.stats().outputChars());
            assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(output));
            Files.write(input, new byte[]{0, -1, -2, 42});
            client.analyzeFile(input, output);
            assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(output));
            Files.write(input, new byte[0]);
            client.analyzeFile(input, output);
            assertEquals(0, Files.size(output));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, input));
            Path link = directory.resolve("hardlink");
            Files.createLink(link, input);
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, link));
            Files.delete(link);
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(directory));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, directory.resolve("missing/file")));
        }
        try (var paths = Files.list(directory)) { assertEquals(2, paths.count()); }
    }

    @Test void remoteFailuresAreTypedAndDoNotLeakDiagnostics() throws Exception {
        List<MystemException> failures = List.of(new MystemInvalidOptionsException("secret source"),
                new MystemClosedException("/private/path"), new MystemRequestTimeoutException("secret"),
                new MystemPoolExhaustedException("secret", null), new MystemOutputLimitException("secret"),
                new MystemStartupException("secret", null), new MystemProtocolException("secret", null),
                new MystemProcessException("secret", java.util.OptionalInt.of(7), "secret stderr"), new MystemException("secret"));
        var backend = new Echo();
        try (var server = service(backend).start(); var client = MystemHttpClient.builder(uri(server)).build()) {
            for (var failure : failures) {
                backend.failure = failure;
                var received = assertThrows(failure.getClass(), () -> client.analyze("a"));
                assertFalse(received.getMessage().contains("secret"));
                if (received instanceof MystemProcessException process) {
                    assertEquals(7, process.exitCode().orElseThrow());
                    assertEquals("", process.stderr());
                }
            }
            backend.failure = null;
            assertEquals("later", client.analyze("later").output());
        }
    }

    @Test void closingClientDoesNotStopServiceAndMetadataRemainsAvailable() throws Exception {
        var backend = new Echo();
        try (var server = service(backend).start()) {
            var client = MystemHttpClient.builder(uri(server)).build();
            client.close(); client.close();
            assertEquals(Optional.of(MystemOutputFormat.JSON), client.outputFormat());
            assertThrows(MystemClosedException.class, () -> client.analyze("a"));
            assertThrows(MystemClosedException.class, () -> client.analyzeFile(directory));
            assertEquals(0, backend.closes.get());
            try (var next = MystemHttpClient.builder(uri(server)).build()) { assertEquals("ok", next.analyze("ok").output()); }
        }
        assertEquals(1, backend.closes.get());
    }

    @Test void admissionDeadlineAndShutdownReleaseBackendWork() throws Exception {
        var backend = new Echo();
        backend.block = true;
        try (var server = service(backend).maxConcurrentRequests(1).requestTimeout(Duration.ofMillis(500)).start();
             var client = MystemHttpClient.builder(uri(server)).build(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = tasks.submit(() -> client.analyze("slow"));
            assertTrue(backend.entered.await(5, TimeUnit.SECONDS));
            assertThrows(MystemPoolExhaustedException.class, () -> client.analyze("busy"));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
            assertTrue(backend.interrupted.await(5, TimeUnit.SECONDS));
            backend.block = false;
        }
        assertEquals(1, backend.closes.get());
    }

    @Test void serviceLimitsRejectOversizedJsonAndFilesAndPreserveLocalOutput() throws Exception {
        Path input = directory.resolve("in");
        Path output = directory.resolve("out");
        Files.writeString(input, "x".repeat(200));
        Files.writeString(output, "preserve me");
        try (var server = service(new Echo()).maxRequestBytes(100).maxResponseBytes(20).start();
             var client = MystemHttpClient.builder(uri(server)).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("x".repeat(101)));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input));
            Files.writeString(input, "x".repeat(21));
            assertThrows(MystemOutputLimitException.class, () -> client.analyzeFile(input, output));
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("x".repeat(21)));
            assertEquals("preserve me", Files.readString(output));
            assertEquals("x", client.analyze("x").output());
        }
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void protocolRejectsBadRoutingContentAndAuthBeforeExecution() throws Exception {
        var backend = new Echo();
        try (var server = service(backend).bearerToken("my-token").maxRequestBytes(32).start();
             var http = HttpClient.newHttpClient()) {
            assertThrows(MystemException.class, () -> MystemHttpClient.builder(uri(server)).build());
            try (var client = MystemHttpClient.builder(uri(server)).bearerToken("my-token").build()) {
                assertEquals("ok", client.analyze("ok").output());
            }
            assertEquals(204, http.send(HttpRequest.newBuilder(uri(server).resolve("health/live")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            for (String body : List.of("{}", "{\"text\":1}", "{\"text\":\"x\",\"text\":\"y\"}", "{\"text\":\"x\"}{}", "[1]", "bad")) {
                assertEquals(400, send(http, server, "v1/analyze", "application/json", body));
            }
            assertEquals(404, http.send(HttpRequest.newBuilder(uri(server).resolve("health/live?x=y"))
                    .header("Authorization", "Bearer my-token").build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(415, send(http, server, "v1/analyze", "text/plain", "x"));
            assertEquals(404, send(http, server, "v1/analyze/extra", "application/json", "{}"));
            assertEquals(404, send(http, server, "v1/analyze?x=y", "application/json", "{}"));
            assertEquals(405, send(http, server, "v1/info", "application/json", "{}"));
            var chunked = HttpRequest.newBuilder(uri(server).resolve("v1/files/content"))
                    .header("Authorization", "Bearer my-token").header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(new byte[33]))).build();
            assertEquals(400, http.send(chunked, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(1, backend.calls.get());
        }
    }

    private static int send(HttpClient http, MystemHttpServer server, String path, String type, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(server).resolve(path)).header("Authorization", "Bearer my-token")
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test void parallelRequestsDoNotMixInputsOrOutputs() throws Exception {
        try (var server = service(new Echo()).maxConcurrentRequests(32).start();
             var client = MystemHttpClient.builder(uri(server)).build(); var tasks = Executors.newFixedThreadPool(8)) {
            var requests = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 80; i++) { String text = "Кошка " + i; requests.add(tasks.submit(() -> client.analyze(text).output())); }
            for (int i = 0; i < 80; i++) assertEquals("Кошка " + i, requests.get(i).get(10, TimeUnit.SECONDS));
        }
    }

    static class Echo implements MystemClient {
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        volatile MystemException failure;
        volatile boolean block;
        @Override public Optional<MystemOutputFormat> outputFormat() { return Optional.of(MystemOutputFormat.JSON); }
        @Override public MystemClientExecutionProfile executionProfile() { return MystemClientExecutionProfile.ONE_SHOT_PROCESS_PER_REQUEST; }
        @Override public MystemRawResult analyze(String text) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            entered.countDown();
            if (block) try { new CountDownLatch(1).await(); }
            catch (InterruptedException stop) { interrupted.countDown(); Thread.currentThread().interrupt(); throw new MystemProtocolException("interrupted", stop); }
            return new MystemRawResult(text, text, MystemOutputFormat.JSON,
                    new MystemRequestStats(Duration.ofMillis(7), MystemExecutionMode.ONE_SHOT_TEXT, text.length(),
                            text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, text.length(), text.length()));
        }
        @Override public MystemFileContentResult analyzeFile(Path input) {
            try {
                return new MystemFileContentResult(input, Files.readString(input), MystemOutputFormat.JSON,
                        new MystemRequestStats(Duration.ZERO, MystemExecutionMode.ONE_SHOT_FILE, -1, Files.size(input), Files.readString(input).length(), Files.size(input)));
            } catch (IOException failure) { throw new MystemException("fixture", failure); }
        }
        @Override public MystemFileResult analyzeFile(Path input, Path output) {
            try {
                Files.copy(input, output);
                return new MystemFileResult(input, output, MystemOutputFormat.JSON,
                        new MystemRequestStats(Duration.ZERO, MystemExecutionMode.ONE_SHOT_FILE, -1, Files.size(input), -1, Files.size(output)));
            } catch (IOException failure) { throw new MystemException("fixture", failure); }
        }
        @Override public void close() { closes.incrementAndGet(); }
    }
}
