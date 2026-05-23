package io.github.ulviar.mystem4j;

/**
 * Static entry point for creating MyStem runtime clients.
 */
public final class Mystem {
    private Mystem() {}

    /**
     * Creates a builder for a MyStem client.
     *
     * @return client builder
     */
    public static MystemClientBuilder builder() {
        return new MystemClientBuilder();
    }
}
