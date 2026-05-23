package io.github.ulviar.mystem4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

final class MystemExecutableResolver {
    static final String EXECUTABLE_PROPERTY = "mystem4j.executable";
    static final String EXECUTABLE_ENV = "MYSTEM_PATH";

    private MystemExecutableResolver() {}

    static Path resolve(Optional<Path> explicitExecutable, boolean searchPath) {
        if (explicitExecutable.isPresent()) {
            return requireExecutable(explicitExecutable.get(), "explicit executable");
        }
        String property = System.getProperty(EXECUTABLE_PROPERTY);
        if (property != null && !property.isBlank()) {
            return requireExecutable(Path.of(property), "system property " + EXECUTABLE_PROPERTY);
        }
        String environment = System.getenv(EXECUTABLE_ENV);
        if (environment != null && !environment.isBlank()) {
            return requireExecutable(Path.of(environment), "environment variable " + EXECUTABLE_ENV);
        }
        if (searchPath) {
            return findInPath().orElseThrow(() -> new MystemExecutableNotFoundException(
                    "MyStem executable was not found. Configure executable path, "
                            + EXECUTABLE_PROPERTY
                            + ", "
                            + EXECUTABLE_ENV
                            + ", or PATH."));
        }
        throw new MystemExecutableNotFoundException(
                "MyStem executable was not configured and PATH search is disabled.");
    }

    private static Optional<Path> findInPath() {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(pathValue.split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(entry -> Path.of(entry).resolve(executableName()))
                .filter(Files::isExecutable)
                .findFirst();
    }

    private static String executableName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "mystem.exe" : "mystem";
    }

    private static Path requireExecutable(Path path, String source) {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw new MystemExecutableNotFoundException("MyStem " + source + " is not executable: " + path);
        }
        return path;
    }
}
