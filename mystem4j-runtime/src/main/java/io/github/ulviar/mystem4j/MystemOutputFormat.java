package io.github.ulviar.mystem4j;

/**
 * Raw MyStem output formats; reusable and pooled clients require {@link #JSON}.
 *
 * <p>This setting selects the CLI output format. It neither parses the response nor validates its syntax.
 * File requests use the same format as text requests on the configured client.
 *
 * @see MystemOptions.Builder#format(MystemOutputFormat)
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
