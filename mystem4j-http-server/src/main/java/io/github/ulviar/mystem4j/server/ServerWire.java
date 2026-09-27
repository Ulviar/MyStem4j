package io.github.ulviar.mystem4j.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemOutputLimitException;
import io.github.ulviar.mystem4j.MystemRequestStats;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.http.HttpFields;

/** Server half of the version 1 protocol. */
final class ServerWire {
    private ServerWire() {}

    static String text(InputStream input, int limit) throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(limit).maxNestingDepth(2).build()).build();
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var json = factory.createParser(new InputStreamReader(input, decoder))) {
            if (json.nextToken() != JsonToken.START_OBJECT || json.nextToken() != JsonToken.FIELD_NAME
                    || !"text".equals(json.currentName()) || json.nextToken() != JsonToken.VALUE_STRING) {
                throw new MystemInvalidOptionsException("Expected a text string");
            }
            String text = json.getText();
            if (json.nextToken() != JsonToken.END_OBJECT || json.nextToken() != null) {
                throw new MystemInvalidOptionsException("Unexpected fields or trailing data");
            }
            return text;
        } catch (CharacterCodingException malformed) {
            throw new MystemInvalidOptionsException("JSON request must use valid UTF-8");
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

    static void stats(HttpFields.Mutable headers, MystemOutputFormat format, MystemRequestStats stats) {
        headers.put("X-Mystem-Format", format.name());
        headers.put("X-Mystem-Elapsed", stats.elapsed().toString());
        headers.put("X-Mystem-Mode", stats.mode().name());
        headers.put("X-Mystem-Input-Chars", Long.toString(stats.inputChars()));
        headers.put("X-Mystem-Input-Bytes", Long.toString(stats.inputBytes()));
        headers.put("X-Mystem-Output-Chars", Long.toString(stats.outputChars()));
        headers.put("X-Mystem-Output-Bytes", Long.toString(stats.outputBytes()));
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
