package io.github.ulviar.mystem4j;

/**
 * Creates clients that run an installed MyStem executable and return its raw output.
 *
 * <p>For a single analysis, supply the executable path and close the client with try-with-resources:
 *
 * <pre>{@code
 * import io.github.ulviar.mystem4j.Mystem;
 * import io.github.ulviar.mystem4j.MystemClient;
 * import java.nio.file.Path;
 *
 * try (MystemClient client = Mystem.builder()
 *         .executable(Path.of("/opt/mystem/mystem"))
 *         .build()) {
 *     String json = client.analyze("Кошки спят.").output();
 *     System.out.println(json);
 * }
 * }</pre>
 *
 * <p>Replace the example path with your installed executable. By default, each request starts a process;
 * the output format is JSON and the encoding is UTF-8. For repeated single-line requests, configure
 * {@link MystemClientBuilder#session() a reusable session} or {@link MystemClientBuilder#pooled() a pool}.
 * The runtime does not install MyStem, accept its license, or parse the returned morphology.
 *
 * @see MystemClient
 * @see MystemOptions
 * @see MystemProbe
 */
public final class Mystem {
    private Mystem() {}

    /**
     * Creates an independent builder with the default one-shot configuration.
     *
     * <p>This method does not resolve an executable or start a process. Resolution occurs in
     * {@link MystemClientBuilder#build()}.
     *
     * @return a new mutable client builder
     */
    public static MystemClientBuilder builder() {
        return new MystemClientBuilder();
    }
}
