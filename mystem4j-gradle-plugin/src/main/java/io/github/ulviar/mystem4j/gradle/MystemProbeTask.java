package io.github.ulviar.mystem4j.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs a bounded smoke request against the prepared native executable.
 *
 * <p>The plugin registers {@code mystemProbe} after extraction. The task starts MyStem
 * with {@code --format json}, writes the smoke word in UTF-8 followed by a line separator,
 * and closes stdin. Success requires exit code zero and nonempty output that looks
 * like a JSON array containing the expected text field. This is a smoke check, not a
 * full JSON or morphology validation.
 *
 * <p>The selected executable must run on the build host. For staging a binary for
 * another OS, consume {@link Mystem4jExtension#getPreparedExecutable()} directly.
 */
@DisableCachingByDefault(because = "Executes the prepared native MyStem binary as a smoke check.")
public abstract class MystemProbeTask extends DefaultTask {
    /**
     * Creates a Gradle-managed task whose properties are configured by the plugin or build script.
     */
    public MystemProbeTask() {}

    /**
     * Returns the native executable to launch.
     *
     * @return prepared executable input file
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutableFile();

    /**
     * Returns the process exit wait in seconds, defaulting to {@code 10} when registered by the plugin.
     * Configure a positive value. The wait starts after writing smoke input; output collection
     * can add a short wait after process termination, so this is not an end-to-end deadline.
     *
     * @return process wait timeout property
     */
    @Input
    public abstract Property<Integer> getTimeoutSeconds();

    /**
     * Returns the smoke word, defaulting to {@code мама} when registered by the plugin.
     * Use a simple word without JSON escapes or line breaks: the smoke check searches for
     * a literal compact JSON text field matching this value.
     *
     * @return UTF-8 smoke input property
     */
    @Input
    public abstract Property<String> getSmokeInput();

    /**
     * Returns the positive capture limit in bytes, applied separately to stdout and stderr.
     * The plugin supplies {@link Mystem4jExtension#getMaxProbeOutputBytes()}, defaulting to 64 KiB.
     *
     * @return per-stream byte limit property
     */
    @Input
    public abstract Property<Integer> getMaxOutputBytes();

    /**
     * Returns the success marker containing the absolute executable path and a bounded output preview.
     * The marker is diagnostic task output, not a runtime configuration file.
     *
     * @return probe marker output file
     */
    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    /**
     * Executes the smoke request and writes a marker after successful validation.
     *
     * @throws GradleException if the output limit is invalid, process startup or I/O fails,
     *         the wait times out or is interrupted, the process exits unsuccessfully,
     *         the output exceeds its limit, or the smoke response does not match
     */
    @TaskAction
    public void probe() {
        Path executable = getExecutableFile().get().getAsFile().toPath();
        String smokeInput = getSmokeInput().get();
        int timeoutSeconds = getTimeoutSeconds().get();
        int maxOutputBytes = getMaxOutputBytes().get();
        if (maxOutputBytes <= 0) {
            throw new GradleException("maxOutputBytes must be positive.");
        }
        Process process;
        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder(List.of(executable.toString(), "--format", "json"));
            process = processBuilder.start();
        } catch (IOException error) {
            throw new GradleException("Failed to start MyStem executable: " + executable, error);
        }

        CompletableFuture<String> stdout = readAsync(process.getInputStream(), maxOutputBytes);
        CompletableFuture<String> stderr = readAsync(process.getErrorStream(), maxOutputBytes);

        try {
            process.getOutputStream().write((smokeInput + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GradleException("MyStem probe timed out after " + Duration.ofSeconds(timeoutSeconds) + ".");
            }

            String output = await(stdout);
            String errorOutput = await(stderr);
            if (process.exitValue() != 0) {
                throw new GradleException("MyStem probe failed with exit code "
                        + process.exitValue()
                        + stderrMessage(errorOutput));
            }
            if (output.isBlank()) {
                throw new GradleException("MyStem probe produced empty stdout" + stderrMessage(errorOutput));
            }
            validateJsonSmokeOutput(output, smokeInput, errorOutput);
            writeMarker(executable, output);
            getLogger().lifecycle("MyStem probe succeeded: {}", executable);
        } catch (IOException error) {
            process.destroyForcibly();
            throw new GradleException("Failed to write smoke input to MyStem probe.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new GradleException("Interrupted while waiting for MyStem probe.", error);
        }
    }

    private void writeMarker(Path executable, String output) {
        Path marker = getMarkerFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(
                    marker,
                    "executable=" + executable.toAbsolutePath() + System.lineSeparator()
                            + "output=" + trim(output.trim()) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Failed to write MyStem probe marker: " + marker, error);
        }
    }

    private static CompletableFuture<String> readAsync(InputStream input, int maxOutputBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try (input) {
                return new String(readWithLimit(input, maxOutputBytes), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new GradleException("Failed to read MyStem probe output.", error);
            }
        });
    }

    private static byte[] readWithLimit(InputStream input, int maxOutputBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxOutputBytes, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxOutputBytes) {
                throw new GradleException("MyStem probe output exceeded maxOutputBytes: " + maxOutputBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String await(CompletableFuture<String> output) {
        try {
            return output.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while reading MyStem probe output.", error);
        } catch (ExecutionException error) {
            throw new GradleException("Failed to read MyStem probe output.", error.getCause());
        } catch (TimeoutException error) {
            throw new GradleException("Timed out while reading MyStem probe output.", error);
        }
    }

    private static String stderrMessage(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return ".";
        }
        String compact = stderr.length() > 2_000 ? stderr.substring(0, 2_000) : stderr;
        return ". stderr: " + compact;
    }

    private static void validateJsonSmokeOutput(String output, String smokeInput, String stderr) {
        String compact = output.trim();
        if (!compact.startsWith("[") || !compact.endsWith("]") || !compact.contains("\"text\":\"" + smokeInput + "\"")) {
            throw new GradleException(
                    "MyStem probe output does not look like MyStem JSON: " + trim(compact) + stderrMessage(stderr));
        }
    }

    private static String trim(String text) {
        if (text.length() <= 2_000) {
            return text;
        }
        return text.substring(0, 2_000);
    }
}
