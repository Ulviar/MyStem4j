package io.github.ulviar.mystem4j.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClosedException;
import io.github.ulviar.mystem4j.MystemException;
import io.github.ulviar.mystem4j.MystemExecutionMode;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputLimitException;
import io.github.ulviar.mystem4j.MystemPoolExhaustedException;
import io.github.ulviar.mystem4j.MystemProcessException;
import io.github.ulviar.mystem4j.MystemProtocolException;
import io.github.ulviar.mystem4j.MystemRequestStats;
import io.github.ulviar.mystem4j.MystemRequestTimeoutException;
import io.github.ulviar.mystem4j.MystemStartupException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.Duration;

/** Client half of the version 1 protocol. No morphology parsing happens here. */
final class ClientWire {
    private ClientWire() {}

    static byte[] text(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var factory = JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();
        try (var json = factory.createGenerator(bytes)) {
            json.writeStartObject();
            json.writeStringField("text", text);
            json.writeEndObject();
        }
        return bytes.toByteArray();
    }

    static String output(byte[] bytes, int limit) throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(limit).maxNestingDepth(2).build()).build();
        try (var json = factory.createParser(bytes)) {
            if (json.nextToken() != JsonToken.START_OBJECT || json.nextToken() != JsonToken.FIELD_NAME
                    || !"output".equals(json.currentName()) || json.nextToken() != JsonToken.VALUE_STRING) {
                throw new IOException("Expected an output string");
            }
            String value = json.getText();
            if (json.nextToken() != JsonToken.END_OBJECT || json.nextToken() != null) {
                throw new IOException("Unexpected response fields or trailing data");
            }
            return value;
        }
    }

    static String header(HttpHeaders headers, String name) {
        var values = headers.allValues("X-Mystem-" + name);
        if (values.size() != 1) throw new IllegalArgumentException("Missing or repeated protocol header: " + name);
        return values.getFirst();
    }

    static MystemRequestStats stats(HttpHeaders headers) {
        return new MystemRequestStats(Duration.parse(header(headers, "Elapsed")),
                MystemExecutionMode.valueOf(header(headers, "Mode")),
                Long.parseLong(header(headers, "Input-Chars")), Long.parseLong(header(headers, "Input-Bytes")),
                Long.parseLong(header(headers, "Output-Chars")), Long.parseLong(header(headers, "Output-Bytes")));
    }

    static MystemException error(int status, HttpHeaders headers) {
        String code = headers.firstValue("X-Mystem-Error").orElse("HTTP_" + status);
        String message = "MyStem service returned " + status + " (" + code + ")";
        return switch (code) {
            case "INVALID_REQUEST" -> new MystemInvalidOptionsException(message);
            case "CLOSED" -> new MystemClosedException(message);
            case "TIMEOUT" -> new MystemRequestTimeoutException(message);
            case "BUSY" -> new MystemPoolExhaustedException(message, null);
            case "OUTPUT_LIMIT" -> new MystemOutputLimitException(message);
            case "PROTOCOL" -> new MystemProtocolException(message, null);
            case "PROCESS" -> new MystemProcessException(message,
                    headers.firstValue("X-Mystem-Exit-Code").map(value -> java.util.OptionalInt.of(Integer.parseInt(value)))
                            .orElse(java.util.OptionalInt.empty()), "");
            case "STARTUP" -> new MystemStartupException(message, null);
            default -> new MystemException(message);
        };
    }
}
