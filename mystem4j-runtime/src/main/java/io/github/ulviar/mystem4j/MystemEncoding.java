package io.github.ulviar.mystem4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Character encodings accepted by MyStem ({@code -e}) for process input and output.
 *
 * <p>UTF-8 is the default and preserves the widest range of input characters. The legacy Cyrillic
 * encodings cannot represent arbitrary Unicode text. Built-in clients use replacement rather than strict
 * rejection for unrepresentable input or malformed process output, so choose an encoding that can
 * represent the caller's text when exact character preservation matters.
 *
 * <p>The same encoding is used for text input, captured stdout, and encoded byte limits. For file
 * requests, the caller must supply file contents in that encoding; the runtime does not transcode files.
 *
 * @see MystemOptions.Builder#encoding(MystemEncoding)
 */
public enum MystemEncoding {
    /** DOS Cyrillic (IBM866). */
    CP866("cp866", Charset.forName("IBM866")),
    /** Windows Cyrillic (Windows-1251). */
    CP1251("cp1251", Charset.forName("windows-1251")),
    /** KOI8-R Cyrillic. */
    KOI8_R("koi8-r", Charset.forName("KOI8-R")),
    /** UTF-8; the default encoding. */
    UTF_8("utf-8", StandardCharsets.UTF_8);

    private final String cliName;
    private final Charset charset;

    MystemEncoding(String cliName, Charset charset) {
        this.cliName = cliName;
        this.charset = charset;
    }

    String cliName() {
        return cliName;
    }

    Charset charset() {
        return charset;
    }
}
