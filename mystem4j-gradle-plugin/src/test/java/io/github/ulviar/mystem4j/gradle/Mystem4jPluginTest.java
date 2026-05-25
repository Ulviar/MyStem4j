package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Mystem4jPluginTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void registersExtensionAndTasks() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(Mystem4jPlugin.class);

        Mystem4jExtension extension = project.getExtensions().getByType(Mystem4jExtension.class);
        assertFalse(extension.getDownload().get());
        assertFalse(extension.getAcceptYandexMystemLicense().get());
        assertFalse(extension.getConfigureTests().get());
        assertNotNull(project.getTasks().findByName("mystemDownload"));
        assertNotNull(project.getTasks().findByName("mystemExtract"));
        assertNotNull(project.getTasks().findByName("mystemProbe"));
        assertNotNull(project.getTasks().findByName("mystemPrepareTestRuntime"));
        assertNotNull(project.getTasks().findByName("mystemPrepareDistribution"));
    }

    @Test
    void wiresPreparedExecutableIntoTestTaskDuringConfiguration() throws IOException {
        Path archive = fakeWindowsArchive();
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                import org.gradle.api.provider.Provider
                import org.gradle.api.tasks.testing.Test

                plugins {
                    java
                    id("io.github.ulviar.mystem4j")
                }

                mystem4j {
                    targetOs.set("windows")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                    configureTests.set(true)
                }

                tasks.register("verifyTestWiring") {
                    dependsOn(tasks.named("test"))
                    doLast {
                        val testTask = tasks.named<Test>("test").get()
                        fun providerValue(value: Any?): String = when (value) {
                            is Provider<*> -> value.get().toString()
                            null -> ""
                            else -> value.toString()
                        }
                        val executable = providerValue(testTask.systemProperties["mystem4j.executable"])
                        check(executable.isNotBlank()) { "missing mystem4j.executable" }
                        check(file(executable).isFile) { "missing executable: $executable" }
                        check(providerValue(testTask.environment["MYSTEM_PATH"]) == executable) { "missing MYSTEM_PATH" }
                        check(testTask.inputs.properties.containsKey("mystem4j.executable")) { "missing test input" }
                    }
                }
                """
                        .formatted(archive.toUri()),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("verifyTestWiring", "--stacktrace")
                .build();
    }

    @Test
    void testRuntimeWiringIsConfigurationCacheCompatible() throws IOException {
        Path archive = fakeWindowsArchive();
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("io.github.ulviar.mystem4j")
                }

                mystem4j {
                    targetOs.set("windows")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                    configureTests.set(true)
                }
                """
                        .formatted(archive.toUri()),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("test", "--configuration-cache", "--stacktrace")
                .build();
        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("test", "--configuration-cache", "--stacktrace")
                .build();
    }

    @Test
    void checksummedDownloadCanReuseSharedCacheWhenOffline() throws IOException {
        Path archive = fakeWindowsArchive();
        String checksum = sha256(archive);
        Path gradleHome = temporaryDirectory.resolve("gradle-home");
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    id("io.github.ulviar.mystem4j")
                }

                mystem4j {
                    targetOs.set("windows")
                    archiveUrl.set("%s")
                    sha256.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                }
                """
                        .formatted(archive.toUri(), checksum),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("mystemDownload", "--gradle-user-home", gradleHome.toString(), "--stacktrace")
                .build();

        Files.delete(archive);
        deleteRecursively(temporaryDirectory.resolve("build"));

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments(
                        "mystemDownload", "--gradle-user-home", gradleHome.toString(), "--offline", "--stacktrace")
                .build();
    }

    private Path fakeWindowsArchive() throws IOException {
        Path executable = temporaryDirectory.resolve("archive-content/mystem.exe");
        Path archive = temporaryDirectory.resolve("mystem.zip");
        Files.createDirectories(executable.getParent());
        Files.writeString(
                executable,
                """
                #!/bin/sh
                while IFS= read -r input; do
                  printf '[{"text":"%s"}]\\n' "$input"
                done
                """,
                StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("mystem.exe"));
            Files.copy(executable, zip);
            zip.closeEntry();
        }
        return archive;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException error) {
            throw new AssertionError("Failed to read " + file + " while calculating sha256.", error);
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("Current JDK does not provide SHA-256.", error);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
