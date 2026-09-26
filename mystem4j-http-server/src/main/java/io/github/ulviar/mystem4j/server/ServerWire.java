package io.github.ulviar.mystem4j.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.sun.net.httpserver.Headers;
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemOutputLimitException;
import io.github.ulviar.mystem4j.MystemRequestStats;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Server half of the version 1 protocol. */
final class ServerWire {
    private ServerWire() {}

    static String text(InputStream input, int limit) throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(limit).maxNestingDepth(2).build()).build();
        try (var json = factory.createParser(input)) {
            if (json.nextToken() != JsonToken.START_OBJECT || json.nextToken() != JsonToken.FIELD_NAME
                    || !"text".equals(json.currentName()) || json.nextToken() != JsonToken.VALUE_STRING) {
                throw new MystemInvalidOptionsException("Expected a text string");
            }
            String text = json.getText();
            if (json.nextToken() != JsonToken.END_OBJECT || json.nextToken() != null) {
                throw new MystemInvalidOptionsException("Unexpected fields or trailing data");
            }
            return text;
        }
    }

    static byte[] output(String text, int limit) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var json = JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build()
                .createGenerator(new OutputStream() {
                    @Override public void write(int value) {
                        if (bytes.size() == limit) throw new MystemOutputLimitException("HTTP response limit exceeded");
                        bytes.write(value);
                    }
                    @Override public void write(byte[] value, int start, int size) {
                        if (size > limit - bytes.size()) throw new MystemOutputLimitException("HTTP response limit exceeded");
                        bytes.write(value, start, size);
                    }
                })) {
            json.writeStartObject();
            json.writeStringField("output", text);
            json.writeEndObject();
        }
        return bytes.toByteArray();
    }

    static void stats(Headers headers, MystemOutputFormat format, MystemRequestStats stats) {
        headers.set("X-Mystem-Format", format.name());
        headers.set("X-Mystem-Elapsed", stats.elapsed().toString());
        headers.set("X-Mystem-Mode", stats.mode().name());
        headers.set("X-Mystem-Input-Chars", Long.toString(stats.inputChars()));
        headers.set("X-Mystem-Input-Bytes", Long.toString(stats.inputBytes()));
        headers.set("X-Mystem-Output-Chars", Long.toString(stats.outputChars()));
        headers.set("X-Mystem-Output-Bytes", Long.toString(stats.outputBytes()));
    }

    static InputStream bounded(InputStream input, int limit) {
        return new java.io.FilterInputStream(input) {
            private long remaining = (long) limit + 1;
            @Override public int read() throws IOException {
                int value = super.read();
                if (value != -1 && --remaining <= 0) throw new MystemInvalidOptionsException("HTTP request limit exceeded");
                return value;
            }
            @Override public int read(byte[] bytes, int start, int size) throws IOException {
                int count = in.read(bytes, start, (int) Math.min(size, remaining));
                if (count > 0 && (remaining -= count) <= 0) throw new MystemInvalidOptionsException("HTTP request limit exceeded");
                return count;
            }
        };
    }
}
