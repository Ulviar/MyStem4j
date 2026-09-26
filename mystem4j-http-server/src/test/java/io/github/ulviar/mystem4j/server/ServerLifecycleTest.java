package io.github.ulviar.mystem4j.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.ulviar.mystem4j.http.MystemHttpClient;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLifecycleTest {
    @TempDir Path directory;

    @Test void stalledUploadTimesOutAndTemporaryFilesAreRemoved() throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        try (var server = MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .temporaryDirectory(directory).requestTimeout(Duration.ofMillis(250)).shutdownGraceSeconds(0).start();
             var socket = new Socket("127.0.0.1", server.address().getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("POST /v1/files/content HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/octet-stream\r\nContent-Length: 100\r\n\r\nx").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            try { assertEquals(-1, socket.getInputStream().read()); }
            catch (java.net.SocketException reset) { assertFalse(socket.isInputShutdown()); }
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        assertEquals(0, backend.calls.get());
    }

    @Test void closeInterruptsAdmittedExecutionBeforeClosingBackend() throws Exception {
        var backend = new HttpServiceContractTest.Echo(); backend.block = true;
        var server = MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .shutdownGraceSeconds(0).start();
        try (var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build();
             var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = tasks.submit(() -> client.analyze("block"));
            assertTrue(backend.entered.await(5, TimeUnit.SECONDS));
            var shutdown = tasks.submit(server::close);
            shutdown.get(5, TimeUnit.SECONDS);
            assertTrue(backend.interrupted.await(5, TimeUnit.SECONDS));
            assertEquals(1, backend.closes.get());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> request.get(5, TimeUnit.SECONDS));
        } finally { server.close(); }
        assertEquals(1, backend.closes.get());
    }

    @Test void failedStartupRetainsCallerOwnershipAndBuilderStartsOnlyOnce() throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        var builder = MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0)).shutdownGraceSeconds(0);
        try (var server = builder.start()) {
            assertThrows(IllegalStateException.class, builder::start);
            var other = new HttpServiceContractTest.Echo();
            assertThrows(java.io.IOException.class, () -> MystemHttpServer.builder(other).address(server.address()).start());
            assertEquals(0, other.closes.get());
            other.close();
        }
        assertEquals(1, backend.closes.get());
    }

    @Test void launcherConfiguresAuthAndLimitsWithoutRunningTheExecutableAtStartup() throws Exception {
        // One-shot construction only resolves the executable. Metadata must not execute it.
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path token = directory.resolve("token");
        Files.writeString(token, "test-token\n");
        var env = new java.util.HashMap<String, String>();
        env.put("MYSTEM_EXECUTABLE", java.toString());
        env.put("MYSTEM_MODE", "oneshot");
        env.put("MYSTEM_PORT", "0");
        env.put("MYSTEM_TOKEN_FILE", token.toString());
        env.put("MYSTEM_TEMP_DIRECTORY", directory.toString());
        env.put("MYSTEM_SHUTDOWN_GRACE_SECONDS", "0");
        try (var server = MystemServerMain.start(env);
             var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).bearerToken("test-token").build()) {
            assertEquals(io.github.ulviar.mystem4j.MystemClientExecutionProfile.ONE_SHOT_PROCESS_PER_REQUEST, client.executionProfile());
        }
        Files.writeString(token, "x".repeat(4097));
        assertThrows(IllegalArgumentException.class, () -> MystemServerMain.start(env));
        Files.delete(token);
        assertThrows(java.io.IOException.class, () -> MystemServerMain.start(env));
    }

    @Test void invalidSettingsFailBeforeOpeningSockets() throws Exception {
        var builder = MystemHttpServer.builder(new HttpServiceContractTest.Echo());
        assertThrows(IllegalArgumentException.class, () -> builder.address(InetSocketAddress.createUnresolved("bad", 1)));
        assertThrows(IllegalArgumentException.class, () -> builder.maxConcurrentRequests(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxRequestBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.maxResponseBytes(0));
        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.shutdownGraceSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.temporaryDirectory(directory.resolve("missing")));
        assertThrows(IllegalArgumentException.class, () -> builder.bearerToken("two words"));
        assertThrows(IllegalArgumentException.class, () -> MystemServerMain.start(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MystemServerMain.start(Map.of("MYSTEM_EXECUTABLE", "missing", "MYSTEM_MODE", "bad")));
        assertThrows(IllegalArgumentException.class, () -> MystemServerMain.start(Map.of("MYSTEM_EXECUTABLE", "missing", "MYSTEM_HTTP_MAX_REQUEST_BYTES", "not-int")));
        assertThrows(IllegalArgumentException.class, () -> MystemServerMain.main(new String[]{"--invalid"}));
        MystemServerMain.main(new String[]{"--help"});
    }
}
