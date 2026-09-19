package io.github.ulviar.mystem4j;

/**
 * Raw MyStem output formats; reusable and pooled clients require {@link #JSON}.
 */
public enum MystemOutputFormat {
    /** MyStem plain-text output; one-shot clients only. */
    TEXT("text"),
    /** MyStem XML output; one-shot clients only. */
    XML("xml"),
    /** MyStem JSON output; the default for all client modes. */
    JSON("json");

    private final String cliName;

    MystemOutputFormat(String cliName) {
        this.cliName = cliName;
    }

    String cliName() {
        return cliName;
    }
}
