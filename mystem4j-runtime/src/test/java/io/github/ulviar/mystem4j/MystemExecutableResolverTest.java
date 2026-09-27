package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemExecutableResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void anchorsRelativePathsFromEveryDiscoverySourceToTheCurrentDirectory() throws IOException {
        Path directory = Files.createTempDirectory(Path.of(""), "mystem-resolver-");
        Path relative = directory.resolve("mystem");
        try {
            Files.writeString(relative, "");
            relative.toFile().setExecutable(true, false);
            for (Path resolved : List.of(
                MystemExecutableResolver.resolve(Optional.of(relative), false, null, null, null, "Linux"),
                MystemExecutableResolver.resolve(Optional.empty(), false, relative.toString(), null, null, "Linux"),
                MystemExecutableResolver.resolve(Optional.empty(), false, null, relative.toString(), null, "Linux"),
                MystemExecutableResolver.resolve(Optional.empty(), true, null, null, relative.getParent().toString(), "Linux"))) {
                assertTrue(resolved.isAbsolute(), resolved.toString());
                assertTrue(Files.isSameFile(relative, resolved));
            }
        } finally {
            Files.deleteIfExists(relative);
            Files.deleteIfExists(directory);
        }
    }

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
    void skipsDirectoryBeforeValidPathCandidate() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        Files.createDirectories(first.resolve("mystem"));
        Path valid = executable("second/mystem");

        assertEquals(valid, MystemExecutableResolver.resolve(Optional.empty(), true, null, null,
                first + File.pathSeparator + valid.getParent(), "Linux"));
    }

    @Test
    void rejectsPathContainingOnlyDirectoryCandidates() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        Files.createDirectories(first.resolve("mystem"));

        assertThrows(MystemExecutableNotFoundException.class,
                () -> MystemExecutableResolver.resolve(Optional.empty(), true, null, null, first.toString(), "Linux"));
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
