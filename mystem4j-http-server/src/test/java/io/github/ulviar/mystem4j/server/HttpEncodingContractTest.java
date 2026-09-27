package io.github.ulviar.mystem4j.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpEncodingContractTest {
    @org.junit.jupiter.api.Test
    void acceptsRawUtf8AndEscapedLoneSurrogatesWithoutChangingCodeUnits() throws Exception {
        String expected = "Кошки 🐈 е\u0308\ud800x\udfff";
        // Raw supplementary/combining characters and isolated surrogate escapes share one body.
        String body = "{\"text\":\"Кошки 🐈 е\u0308\\ud800x\\udfff\"}";
        var received = new AtomicReference<String>();
        var backend = new HttpServiceContractTest.Echo() {
            @Override public io.github.ulviar.mystem4j.MystemRawResult analyze(String text) {
                received.set(text);
                return super.analyze(text);
            }
        };
        try (var server = MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .shutdownGraceSeconds(0).start(); var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(HttpServiceContractTest.uri(server).resolve("v1/analyze"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals(expected, received.get());
            try (var json = new com.fasterxml.jackson.core.JsonFactory().createParser(response.body())) {
                json.nextToken(); json.nextToken(); json.nextToken();
                assertEquals(expected, json.getText());
            }
            assertEquals(1, backend.calls.get());
        }
    }

    static Stream<Arguments> invalidRequests() {
        String json = "{\"text\":\"Кошка\"}";
        var encodings = Stream.of("UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")
                .map(name -> Arguments.of(name, json.getBytes(Charset.forName(name))));
        var malformed = Stream.of(
                new byte[]{(byte) 0xc0, (byte) 0xaf}, // Overlong encoding of '/'.
                new byte[]{(byte) 0xe0, (byte) 0x80, (byte) 0xaf},
                new byte[]{(byte) 0xf0, (byte) 0x80, (byte) 0x80, (byte) 0xaf},
                new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80}, // Raw surrogate.
                new byte[]{(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, // Above U+10FFFF.
                new byte[]{(byte) 0x80}, new byte[]{(byte) 0xe2, (byte) 0x82})
                .map(value -> Arguments.of(java.util.HexFormat.of().formatHex(value), envelope(value)));
        return Stream.concat(encodings, malformed);
    }

    private static byte[] envelope(byte[] value) {
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.writeBytes("{\"text\":\"".getBytes(StandardCharsets.US_ASCII));
        bytes.writeBytes(value);
        bytes.writeBytes("\"}".getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsNonUtf8AndMalformedSequencesBeforeBackendExecution(String label, byte[] body) throws Exception {
        var backend = new HttpServiceContractTest.Echo();
        try (var server = MystemHttpServer.builder(backend).address(new InetSocketAddress("127.0.0.1", 0))
                .shutdownGraceSeconds(0).start(); var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(HttpServiceContractTest.uri(server).resolve("v1/analyze"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(400, response.statusCode(), label);
            assertEquals("INVALID_REQUEST", response.headers().firstValue("X-Mystem-Error").orElseThrow());
            assertEquals(0, response.body().length);
            assertEquals(0, backend.calls.get());
        }
    }
}
