package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.gradle.api.GradleException;

final class ProcessSupport {
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private ProcessSupport() {}

    static String run(List<String> command, File workingDirectory) {
        return run(command, workingDirectory, Duration.ofMinutes(2));
    }

    static String run(List<String> command, File workingDirectory, Duration timeout) {
        Process process = null;
        var readers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Process child = new ProcessBuilder(command).directory(workingDirectory).start();
            process = child;
            child.getOutputStream().close();
            var stdout = readers.submit(() -> capture(child.getInputStream()));
            var stderr = readers.submit(() -> capture(child.getErrorStream()));
            if (!child.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new GradleException("Command timed out after " + timeout + ": " + String.join(" ", command));
            }
            Captured standardOutput = stdout.get(5, TimeUnit.SECONDS);
            Captured errorOutput = stderr.get(5, TimeUnit.SECONDS);
            if (standardOutput.truncated() || errorOutput.truncated()) {
                throw new GradleException("Command output exceeded " + MAX_OUTPUT_BYTES + " bytes per stream: "
                        + String.join(" ", command));
            }
            if (child.exitValue() != 0) {
                throw new GradleException("Command failed (exit " + child.exitValue() + "): "
                        + String.join(" ", command) + "\n" + errorOutput.text());
            }
            return standardOutput.text();
        } catch (IOException | ExecutionException | TimeoutException error) {
            throw new GradleException("Failed to run command: " + String.join(" ", command), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while running command: " + String.join(" ", command), error);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            readers.shutdownNow();
        }
    }

    private static Captured capture(InputStream stream) throws IOException {
        try (stream; var bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            boolean truncated = false;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                int retained = Math.min(count, MAX_OUTPUT_BYTES - bytes.size());
                bytes.write(buffer, 0, retained);
                truncated |= retained < count;
            }
            return new Captured(bytes.toString(StandardCharsets.UTF_8), truncated);
        }
    }

    private record Captured(String text, boolean truncated) {}
}
