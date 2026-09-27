package io.github.ulviar.mystem4j.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.ulviar.mystem4j.*;
import io.github.ulviar.mystem4j.http.MystemHttpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JettyTransportTest {
    @TempDir Path directory;

    private MystemHttpServer.Builder service(MystemClient backend) {
        return MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .temporaryDirectory(directory).shutdownGraceSeconds(0);
    }

    @Test void parserRejectsOversizedHeadersAndAmbiguousFramingWithoutLeakingRequestData() throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        try (var server = service(backend).start()) {
            String oversized = exchange(server, "GET /private-secret HTTP/1.1\r\nHost: localhost\r\n"
                    + "X-Secret: " + "z".repeat(20_000) + "\r\n\r\n");
            assertTrue(oversized.startsWith("HTTP/1.1 431"), oversized);
            assertFalse(oversized.contains("private-secret"));
            assertFalse(oversized.contains("zzzz"));
            assertTrue(oversized.endsWith("\r\n\r\n"), oversized);
            String ambiguous = exchange(server, "POST /v1/analyze HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/json\r\nContent-Length: 12\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n");
            assertTrue(ambiguous.startsWith("HTTP/1.1 400"), ambiguous);
            assertEquals(0, backend.calls.get());
        }
    }

    @Test void rejectedIncompleteBodiesReturnProtocolErrorsWithoutWaitingForEof() throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        try (var server = service(backend).maxRequestBytes(32).requestTimeout(Duration.ofSeconds(1)).start()) {
            for (String body : new String[]{
                    "POST /v1/files/content HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/octet-stream\r\nTransfer-Encoding: chunked\r\n\r\n"
                    + "100\r\n" + "x".repeat(64),
                    "POST /v1/analyze HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/json\r\nContent-Length: 30\r\n\r\n{}  "
            }) {
                String response = responseHeaders(server, body);
                assertTrue(response.startsWith("HTTP/1.1 400"), response);
                assertTrue(response.contains("X-Mystem-Error: INVALID_REQUEST"), response);
                assertTrue(response.toLowerCase(java.util.Locale.ROOT).contains("content-length: 0"), response);
            }
            assertEquals(0, backend.calls.get());
        }
        assertNoTemporaryFiles();
    }

    @Test void encodedPathsDoNotAliasVersionedRoutesAndKeepAliveHasNoStaleDeadline() throws Exception {
        try (var server = service(new HttpServiceContractTest.Echo()).requestTimeout(Duration.ofMillis(200)).start()) {
            assertTrue(exchange(server, "POST /v1/%61nalyze HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/json\r\nContent-Length: 12\r\n\r\n{\"text\":\"x\"}")
                    .startsWith("HTTP/1.1 404"));
            try (var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build()) {
                for (int i = 0; i < 6; i++) {
                    assertEquals("request " + i, client.analyze("request " + i).output());
                    Thread.sleep(60);
                }
            }
        }
    }

    @Test void slowContinuousUploadCannotExtendAbsoluteDeadline() throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        try (var server = service(backend).requestTimeout(Duration.ofMillis(300)).start();
             var socket = connect(server); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            socket.getOutputStream().write(("POST /v1/files/content HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/octet-stream\r\nContent-Length: 100000\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            var upload = tasks.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        socket.getOutputStream().write('x');
                        socket.getOutputStream().flush();
                        Thread.sleep(25);
                    }
                } catch (IOException closed) { /* Expected deadline disconnect. */ }
                catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
            });
            try { assertEquals(-1, socket.getInputStream().read()); }
            catch (java.net.SocketException reset) { /* Both FIN and RST terminate the exchange. */ }
            upload.cancel(true);
        }
        assertEquals(0, backend.calls.get());
        assertNoTemporaryFiles();
    }

    @Test void stalledDownloadReleasesAdmissionAndDeletesNativeOutput() throws Exception {
        var outputReady = new CountDownLatch(1);
        var backend = new HttpServiceContractTest.Echo() {
            @Override public MystemFileResult analyzeFile(Path input, Path output) {
                try (var file = java.nio.channels.FileChannel.open(output,
                        java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
                    file.position(32 * 1024 * 1024 - 1);
                    file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
                } catch (IOException failure) { throw new MystemException("fixture", failure); }
                outputReady.countDown();
                return new MystemFileResult(input, output, MystemOutputFormat.JSON,
                        new MystemRequestStats(Duration.ZERO, MystemExecutionMode.ONE_SHOT_FILE, -1, 0, -1, 32 * 1024 * 1024));
            }
        };
        try (var server = service(backend).maxConcurrentRequests(1).requestTimeout(Duration.ofMillis(500)).start();
             var socket = connect(server)) {
            socket.setReceiveBufferSize(1024);
            socket.getOutputStream().write(("POST /v1/files/output HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/octet-stream\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            assertTrue(outputReady.await(5, TimeUnit.SECONDS));
            // Do not consume the response: socket backpressure must be covered by the deadline too.
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (hasTemporaryFiles() && System.nanoTime() < end) Thread.sleep(20);
            assertNoTemporaryFiles();
            try (var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build()) {
                assertEquals("recovered", client.analyze("recovered").output());
            }
        }
    }

    @Test void gracefulStopLetsAdmittedWorkFinishOnVirtualThread() throws Exception {
        var release = new CountDownLatch(1);
        var analysisEntered = new CountDownLatch(1);
        var backend = new HttpServiceContractTest.Echo() {
            @Override public MystemRawResult analyze(String text) {
                assertTrue(Thread.currentThread().isVirtual());
                analysisEntered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException stop) { throw new AssertionError("Interrupted before grace expired", stop); }
                assertEquals(0, closes.get());
                return super.analyze(text);
            }
        };
        var server = service(backend).shutdownGraceSeconds(3).start();
        var address = server.address();
        try (var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build();
             var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = tasks.submit(() -> client.analyze("drain"));
            assertTrue(analysisEntered.await(5, TimeUnit.SECONDS));
            var closing = new CountDownLatch(1);
            var shutdown = tasks.submit(() -> { closing.countDown(); server.close(); });
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> shutdown.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("drain", request.get(5, TimeUnit.SECONDS).output());
            shutdown.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); server.close(); }
        assertEquals(1, backend.closes.get());
        assertEquals(address, server.address());
    }

    @Test void expiredGraceInterruptsBackendAndStillCompletesClose() throws Exception {
        var backend = new HttpServiceContractTest.Echo(); backend.block = true;
        var server = service(backend).shutdownGraceSeconds(1).start();
        try (var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build();
             var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = tasks.submit(() -> client.analyze("block"));
            assertTrue(backend.entered.await(5, TimeUnit.SECONDS));
            tasks.submit(server::close).get(5, TimeUnit.SECONDS);
            assertTrue(backend.interrupted.await(1, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> request.get(5, TimeUnit.SECONDS));
        } finally { server.close(); }
        assertEquals(1, backend.closes.get());
    }

    private boolean hasTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) { return files.findAny().isPresent(); }
    }

    private void assertNoTemporaryFiles() throws IOException { assertFalse(hasTemporaryFiles()); }

    private static Socket connect(MystemHttpServer server) throws IOException {
        var socket = new Socket("127.0.0.1", server.address().getPort());
        socket.setSoTimeout(5000);
        return socket;
    }

    private static String responseHeaders(MystemHttpServer server, String request) throws IOException {
        try (var socket = connect(server)) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            var headers = new StringBuilder();
            while (!headers.toString().endsWith("\r\n\r\n") && headers.length() < 32_768) {
                int value = socket.getInputStream().read();
                if (value == -1) break;
                headers.append((char) value);
            }
            return headers.toString();
        }
    }

    private static String exchange(MystemHttpServer server, String request) throws IOException {
        try (var socket = connect(server)) {
            request = request.replace("HTTP/1.1\r\n", "HTTP/1.1\r\nConnection: close\r\n");
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
