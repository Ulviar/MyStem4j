package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.gradle.api.GradleException;

final class ProcessSupport {
    private ProcessSupport() {}

    static String run(List<String> command, File workingDirectory) {
        try {
            Process process = new ProcessBuilder(command).directory(workingDirectory).start();
            String standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("Command failed: " + String.join(" ", command) + "\n" + errorOutput);
            }
            return standardOutput;
        } catch (IOException error) {
            throw new GradleException("Failed to run command: " + String.join(" ", command), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while running command: " + String.join(" ", command), error);
        }
    }
}
