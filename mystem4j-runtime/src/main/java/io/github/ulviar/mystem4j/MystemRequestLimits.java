package io.github.ulviar.mystem4j;

import java.nio.charset.Charset;

/** Payload limits are checked before handing a request to a process or pool. */
record MystemRequestLimits(int chars, int bytes, Charset charset) {
    int validate(String text) {
        if (text.length() > chars) {
            throw new MystemInvalidOptionsException(
                    "MyStem request exceeds maxRequestChars: " + text.length() + " > " + chars);
        }
        int encodedBytes = text.getBytes(charset).length;
        if (encodedBytes > bytes) {
            throw new MystemInvalidOptionsException(
                    "MyStem request exceeds maxRequestBytes: " + encodedBytes + " > " + bytes);
        }
        return encodedBytes;
    }

    int framedChars() {
        return withNewline(chars);
    }

    int framedBytes() {
        // All supported MyStem encodings represent LF with one byte.
        return withNewline(bytes);
    }

    private static int withNewline(int limit) {
        return limit == Integer.MAX_VALUE ? limit : limit + 1;
    }
}
