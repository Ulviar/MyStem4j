package io.github.ulviar.mystem4j;

public enum MystemOutputFormat {
    TEXT("text"),
    XML("xml"),
    JSON("json");

    private final String cliName;

    MystemOutputFormat(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }
}
