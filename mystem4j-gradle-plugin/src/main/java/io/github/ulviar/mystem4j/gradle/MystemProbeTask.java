package io.github.ulviar.mystem4j.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Executes the prepared native MyStem binary as a smoke check.")
public abstract class MystemProbeTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutableFile();

    @Input
    public abstract Property<Integer> getTimeoutSeconds();

    @Input
    public abstract Property<String> getSmokeInput();

    @Input
    public abstract Property<Integer> getMaxOutputBytes();

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
