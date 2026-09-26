package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Checks that an executable can run a small MyStem JSON request.
 *
 * <p>A probe starts and closes a separate one-shot process. It validates the basic smoke-response
 * structure, not a MyStem version, linguistic accuracy, or compatibility with every option. Use it before
 * opening a long-lived client when an early executable check is useful.
 *
 * @see MystemProbeResult
 */
public final class MystemProbe {
    private MystemProbe() {}

    /**
     * Probes a discovered executable with the default five-second request timeout.
     *
     * <p>Resolution uses {@code mystem4j.executable}, then {@code MYSTEM_PATH}, then PATH, with the same
     * precedence as {@link MystemClientBuilder#searchPath(boolean)}.
     *
     * @return validated smoke-response metadata
     * @throws MystemExecutableNotFoundException when no regular executable can be resolved
     * @throws MystemException when execution or smoke-response validation fails
     */
    public static MystemProbeResult probe() {
        return probe(MystemExecutableResolver.resolve(Optional.empty(), true));
    }

    /**
     * Probes an explicit MyStem executable with the default five-second timeout.
     *
     * @param executable non-null path to a regular executable file
     * @return validated smoke-response metadata
     * @throws NullPointerException when {@code executable} is null
     * @throws MystemExecutableNotFoundException when the path is not a regular executable file
     * @throws MystemException when execution or smoke-response validation fails
     */
    public static MystemProbeResult probe(Path executable) {
        return probe(executable, Duration.ofSeconds(5));
    }

    /**
     * Probes an explicit MyStem executable by running one JSON smoke request.
     *
     * <p>The timeout bounds waiting for the started process to exit. Executable resolution, process startup,
     * output collection, cleanup, and response validation can add time. The child process is closed before
     * this method returns or throws.
     *
     * @param executable non-null path to a regular executable file
     * @param timeout positive process exit wait timeout
     * @return validated smoke-response metadata
     * @throws NullPointerException when {@code executable} or {@code timeout} is null
     * @throws MystemExecutableNotFoundException when the path is not a regular executable file
     * @throws MystemProtocolException when the response does not pass the smoke check
     * @throws MystemException when execution or smoke-response validation fails
     * @throws IllegalArgumentException when timeout is zero or negative
     */
    public static MystemProbeResult probe(Path executable, Duration timeout) {
        Path resolvedExecutable = MystemExecutableResolver.resolve(Optional.of(executable), false);
        try (MystemClient client = Mystem.builder()
                .executable(resolvedExecutable)
                .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                .requestTimeout(timeout)
                .build()) {
            MystemRawResult result = client.analyze(MystemProbeValidator.SMOKE_TEXT);
            MystemProbeValidator.validateJsonSmokeOutput(result.output());
            return new MystemProbeResult(
                    resolvedExecutable, result.stats().elapsed(), result.format(), result.output());
        }
    }
}
