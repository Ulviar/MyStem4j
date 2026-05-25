package io.github.ulviar.mystem4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum MystemEncoding {
    CP866("cp866", Charset.forName("IBM866")),
    CP1251("cp1251", Charset.forName("windows-1251")),
    KOI8_R("koi8-r", Charset.forName("KOI8-R")),
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
