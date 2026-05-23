package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Smoke checks for a MyStem executable.
 */
public final class MystemProbe {
    private MystemProbe() {}

    /**
     * Probes a MyStem executable resolved from configured system property, environment, or PATH.
     *
     * @return probe result
     */
    public static MystemProbeResult probe() {
        return probe(MystemExecutableResolver.resolve(Optional.empty(), true));
    }

    /**
     * Probes an explicit MyStem executable with the default timeout.
     *
     * @param executable executable path
     * @return probe result
     */
    public static MystemProbeResult probe(Path executable) {
        return probe(executable, Duration.ofSeconds(5));
    }

    /**
     * Probes an explicit MyStem executable by running one JSON smoke request.
     *
     * @param executable executable path
     * @param timeout request timeout
     * @return probe result
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
