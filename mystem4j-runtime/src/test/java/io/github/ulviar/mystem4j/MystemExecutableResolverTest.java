package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemExecutableResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesExplicitExecutableBeforeOtherSources() throws IOException {
        Path explicit = executable("explicit/mystem");
        Path property = executable("property/mystem");

        assertEquals(
                explicit,
                MystemExecutableResolver.resolve(
                        Optional.of(explicit),
                        true,
                        property.toString(),
                        null,
                        property.getParent().toString(),
                        "Linux"));
    }

    @Test
    void resolvesSystemPropertyBeforeEnvironmentAndPath() throws IOException {
        Path property = executable("property/mystem");
        Path environment = executable("environment/mystem");

        assertEquals(
                property,
                MystemExecutableResolver.resolve(
                        Optional.empty(),
                        true,
                        property.toString(),
                        environment.toString(),
                        environment.getParent().toString(),
                        "Linux"));
    }

    @Test
    void resolvesEnvironmentBeforePath() throws IOException {
        Path environment = executable("environment/mystem");
        Path pathExecutable = executable("path/mystem");

        assertEquals(
                environment,
                MystemExecutableResolver.resolve(
                        Optional.empty(),
                        true,
                        "",
                        environment.toString(),
                        pathExecutable.getParent().toString(),
                        "Linux"));
    }

    @Test
    void resolvesFromPathWhenEnabled() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        Files.createDirectories(first);
        Path executable = executable("second/mystem");

        assertEquals(
                executable,
                MystemExecutableResolver.resolve(
                        Optional.empty(),
                        true,
                        null,
                        null,
                        first + File.pathSeparator + second,
                        "Linux"));
    }

    @Test
    void rejectsMissingExecutableWhenPathSearchDisabled() {
        assertThrows(
                MystemExecutableNotFoundException.class,
                () -> MystemExecutableResolver.resolve(Optional.empty(), false, null, null, null, "Linux"));
    }

    private Path executable(String relativePath) throws IOException {
        Path executable = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "");
        executable.toFile().setExecutable(true, false);
        return executable;
    }
}
