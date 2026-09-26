package io.github.ulviar.mystem4j.http;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ulviar.mystem4j.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpClientFailureTest {
    @TempDir Path directory;
    @Test void deadlineIncludesResponseBodyAndClientRemainsUsable() throws Exception {
        try (var remote = new Remote(exchange -> {
            headers(exchange);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { Thread.sleep(5000); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        }); var client = remote.builder().requestTimeout(Duration.ofMillis(300)).build()) {
            assertThrows(MystemRequestTimeoutException.class, () -> client.analyze("x"));
        }
    }

    @Test void clientLimitRejectsChunkedResponsesAndPreservesDestination() throws Exception {
        Path input = directory.resolve("input");
        Path output = directory.resolve("output");
        Files.writeString(input, "input"); Files.writeString(output, "original");
        try (var remote = new Remote(exchange -> {
            headers(exchange);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[1000]);
        }); var client = remote.builder().maxResponseBytes(50).build()) {
            assertThrows(MystemOutputLimitException.class, () -> client.analyze("x"));
            assertThrows(MystemOutputLimitException.class, () -> client.analyzeFile(input, output));
            assertEquals("original", Files.readString(output));
        }
        try (var paths = Files.list(directory)) { assertEquals(2, paths.count()); }
    }

    @SuppressWarnings("try") // Explicit close is the behavior under test.
    @Test void interruptionPreservesFlagAndCloseCancelsRequests() throws Exception {
        var entered = new CountDownLatch(1);
        try (var remote = new Remote(exchange -> {
            entered.countDown();
            try { Thread.sleep(5000); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        }); var client = remote.builder().build(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = tasks.submit(() -> {
                Thread.currentThread().interrupt();
                assertThrows(MystemProtocolException.class, () -> client.analyze("x"));
                assertTrue(Thread.currentThread().isInterrupted());
            });
            first.get(5, TimeUnit.SECONDS);
            var second = tasks.submit(() -> assertThrows(MystemClosedException.class, () -> client.analyze("y")));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            client.close();
            second.get(5, TimeUnit.SECONDS);
            assertThrows(MystemClosedException.class, () -> client.analyze("x"));
        }
    }

    @Test void truncatedFileDownloadLeavesOriginalDestinationAndRemovesTemporaryFile() throws Exception {
        Path input = directory.resolve("input"); Path output = directory.resolve("output");
        Files.writeString(input, "a"); Files.writeString(output, "original");
        try (var remote = new Remote(exchange -> {
            headers(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write(new byte[8]);
            exchange.getResponseBody().flush();
            // Closing a fixed-length response early simulates a failed download after bytes reach disk.
        }); var client = remote.builder().build()) {
            assertThrows(MystemProtocolException.class, () -> client.analyzeFile(input, output));
            assertEquals("original", Files.readString(output));
        }
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void malformedMetadataJsonAndFormatChangesFailClosed() throws Exception {
        for (String body : java.util.List.of("{}", "{\"output\":1}", "{\"output\":\"x\"}{}", "{\"output\":\"x\",\"extra\":0}")) {
            try (var remote = new Remote(exchange -> respond(exchange, body)); var client = remote.builder().build()) {
                assertThrows(MystemProtocolException.class, () -> client.analyze("x"));
            }
        }
        try (var remote = new Remote(exchange -> {
            headers(exchange); exchange.getResponseHeaders().set("X-Mystem-Format", "XML");
            byte[] body = "{\"output\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
        }); var client = remote.builder().build()) {
            assertThrows(MystemProtocolException.class, () -> client.analyze("x"));
        }
    }

    @Test void successfulTextAndFileCallsValidateStatisticsAndLocalArguments() throws Exception {
        Path input = directory.resolve("input"); Path output = directory.resolve("output");
        Files.writeString(input, "a");
        try (var remote = new Remote(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/output")) {
                headers(exchange); exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, 3); exchange.getResponseBody().write(new byte[]{1, 2, 3});
            } else respond(exchange, "{\"output\":\"\\ud800Кошка🐈\\udfff\"}");
        }); var client = remote.builder().build()) {
            assertEquals("\ud800Кошка🐈\udfff", client.analyze("original").output());
            assertEquals("original", client.analyze("original").input());
            assertEquals(input, client.analyzeFile(input).input());
            assertEquals(output, client.analyzeFile(input, output).output());
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(output));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(input, input));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyzeFile(directory));
        }
        try (var remote = new Remote(exchange -> respond(exchange, "{\"output\":\"a\"}"));
             var client = remote.builder().maxRequestBytes(1).build()) {
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("long"));
            assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("a"));
        }
    }

    @Test void builderRejectsInvalidConfigurationBeforeNetworkAccess() {
        for (String uri : java.util.List.of("relative", "ftp://host/", "http://a@host", "http://host/?q=x", "http://host/#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> MystemHttpClient.builder(URI.create(uri)));
        }
        var builder = MystemHttpClient.builder(URI.create("https://localhost/prefix"));
        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ofDays(2)));
        assertThrows(IllegalArgumentException.class, () -> builder.maxRequestBytes(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxResponseBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.bearerToken("\nsecret"));
    }

    @Test void remoteErrorMappingDoesNotExposeUntrustedBody() throws Exception {
        var cases = java.util.Map.of("INVALID_REQUEST", MystemInvalidOptionsException.class, "CLOSED", MystemClosedException.class,
                "TIMEOUT", MystemRequestTimeoutException.class, "BUSY", MystemPoolExhaustedException.class,
                "OUTPUT_LIMIT", MystemOutputLimitException.class, "PROTOCOL", MystemProtocolException.class,
                "STARTUP", MystemStartupException.class, "PROCESS", MystemProcessException.class, "INTERNAL", MystemException.class);
        for (var entry : cases.entrySet()) {
            try (var remote = new Remote(exchange -> {
                exchange.getResponseHeaders().set("X-Mystem-Error", entry.getKey());
                exchange.sendResponseHeaders(500, -1);
            }); var client = remote.builder().build()) {
                assertThrows(entry.getValue(), () -> client.analyze("a"));
            }
        }
    }

    static void headers(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("X-Mystem-Version", "1"); h.set("X-Mystem-Format", "JSON");
        h.set("X-Mystem-Profile", "POOLED_SESSIONS"); h.set("X-Mystem-Elapsed", "PT0.01S");
        h.set("X-Mystem-Mode", "ONE_SHOT_TEXT");
        for (String name : java.util.List.of("Input-Chars", "Input-Bytes", "Output-Chars", "Output-Bytes")) h.set("X-Mystem-" + name, "1");
        h.set("Content-Type", "application/json");
    }
    static void respond(HttpExchange exchange, String body) throws IOException {
        headers(exchange); byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
    }
    static class Remote implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor();
        Remote(com.sun.net.httpserver.HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
            server.setExecutor(tasks);
            server.createContext("/", exchange -> {
                try (exchange) {
                    exchange.getRequestBody().readAllBytes();
                    if (exchange.getRequestURI().getPath().equals("/v1/info")) { headers(exchange); exchange.sendResponseHeaders(204, -1); }
                    else handler.handle(exchange);
                }
            });
            server.start();
        }
        MystemHttpClient.Builder builder() { return MystemHttpClient.builder(URI.create("http://127.0.0.1:" + server.getAddress().getPort())); }
        @Override public void close() { server.stop(0); tasks.shutdownNow(); tasks.close(); }
    }
}
