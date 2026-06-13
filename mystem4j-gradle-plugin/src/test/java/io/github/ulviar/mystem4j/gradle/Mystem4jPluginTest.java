package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
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
        assertNotNull(extension.getDownloadedArchive());
        assertNotNull(extension.getPreparedExecutable());
        assertNotNull(extension.getExecutablePath());
        assertNotNull(project.getTasks().findByName("mystemDownload"));
        assertNotNull(project.getTasks().findByName("mystemExtract"));
        assertNotNull(project.getTasks().findByName("mystemProbe"));
        assertNotNull(project.getTasks().findByName("mystemPrepareTestRuntime"));
    }

    @Test
    void exposesPreparedExecutableForCustomTasksWithoutProbe() throws IOException {
        Path archive = fakeWindowsArchive(
                """
                #!/bin/sh
                exit 23
                """);
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                import org.gradle.api.tasks.Sync

                plugins {
                    id("io.github.ulviar.mystem4j")
                }

                mystem4j {
                    targetOs.set("windows")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                }

                tasks.register<Sync>("stageMystem") {
                    from(mystem4j.preparedExecutable)
                    into(layout.buildDirectory.dir("stage"))
                }

                tasks.register("verifyStage") {
                    dependsOn("stageMystem")
                    doLast {
                        check(file("build/stage/mystem.exe").isFile) { "missing staged executable" }
                    }
                }
                """
                        .formatted(archive.toUri()),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("verifyStage", "--stacktrace")
                .build();
    }

    @Test
    void testTaskRunsWithoutPreparedExecutableByDefault() throws IOException {
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("io.github.ulviar.mystem4j")
                }
                """,
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("test", "--stacktrace")
                .build();
    }

    @Test
    void wiresPreparedExecutableIntoTestTaskDuringConfiguration() throws IOException {
        Path archive = fakeProbeArchive();
        String targetOs = probeTargetOs();
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
                    targetOs.set("%s")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                    configureTests.set(true)
                }

                tasks.register("verifyTestWiring") {
                    dependsOn(tasks.named("test"))
                    doLast {
                        val testTask = tasks.named<Test>("test").get()
                        check(testTask.jvmArgumentProviders.isNotEmpty()) { "missing JVM argument provider" }
                        check(testTask.inputs.properties.containsKey("mystem4j.configureTests")) { "missing configureTests input" }
                        check(file(mystem4j.executablePath.get()).isFile) { "missing executable" }
                    }
                }
                """
                        .formatted(targetOs, archive.toUri()),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("verifyTestWiring", "--stacktrace")
                .build();
    }

    @Test
    void wiresPreparedExecutableIntoForkedTestJvm() throws IOException {
        Path archive = fakeProbeArchive();
        String targetOs = probeTargetOs();
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.createDirectories(temporaryDirectory.resolve("src/test/java"));
        Files.writeString(
                temporaryDirectory.resolve("src/test/java/ConfiguredMystemTest.java"),
                """
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertNotNull;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import org.junit.jupiter.api.Test;

                class ConfiguredMystemTest {
                    @Test
                    void receivesPreparedExecutablePath() {
                        String executable = System.getProperty("mystem4j.executable");
                        assertNotNull(executable);
                        assertTrue(Files.isRegularFile(Path.of(executable)), executable);
                        assertEquals(executable, System.getenv("MYSTEM_PATH"));
                    }
                }
                """,
                StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("io.github.ulviar.mystem4j")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
                }

                tasks.test {
                    useJUnitPlatform()
                }

                mystem4j {
                    targetOs.set("%s")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                    configureTests.set(true)
                }
                """
                        .formatted(targetOs, archive.toUri()),
                StandardCharsets.UTF_8);

        GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withPluginClasspath()
                .withArguments("test", "--stacktrace")
                .build();
    }

    @Test
    void testRuntimeWiringIsConfigurationCacheCompatible() throws IOException {
        Path archive = fakeProbeArchive();
        String targetOs = probeTargetOs();
        Files.writeString(temporaryDirectory.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("io.github.ulviar.mystem4j")
                }

                mystem4j {
                    targetOs.set("%s")
                    archiveUrl.set("%s")
                    download.set(true)
                    acceptYandexMystemLicense.set(true)
                    configureTests.set(true)
                }
                """
                        .formatted(targetOs, archive.toUri()),
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
        return fakeWindowsArchive(
                """
                #!/bin/sh
                while IFS= read -r input; do
                  printf '[{"text":"%s"}]\\n' "$input"
                done
                """);
    }

    private Path fakeWindowsArchive(String executableContent) throws IOException {
        Path executable = temporaryDirectory.resolve("archive-content/mystem.exe");
        Path archive = temporaryDirectory.resolve("mystem.zip");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, executableContent, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("mystem.exe"));
            Files.copy(executable, zip);
            zip.closeEntry();
        }
        return archive;
    }

    private Path fakeProbeArchive() throws IOException {
        assumeFalse(isWindows(), "JVM-only test fixture cannot provide a native Windows mystem.exe shim.");
        Path contentDirectory = temporaryDirectory.resolve("probe-archive-content");
        Files.createDirectories(contentDirectory);
        Path executable = FakeMystemExecutable.create(contentDirectory, "mystem", "echo");
        Path archive = temporaryDirectory.resolve("mystem-probe.tar.gz");
        writeTarGz(archive, "mystem/bin/mystem", Files.readAllBytes(executable));
        return archive;
    }

    private static String probeTargetOs() {
        String os = MystemDistribution.currentOs();
        assumeFalse("windows".equals(os), "JVM-only test fixture cannot provide a native Windows mystem.exe shim.");
        return os;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void writeTarGz(Path archive, String name, byte[] content) throws IOException {
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            byte[] header = new byte[512];
            writeAscii(header, 0, 100, name);
            writeOctal(header, 100, 8, 0755);
            writeOctal(header, 108, 8, 0);
            writeOctal(header, 116, 8, 0);
            writeOctal(header, 124, 12, content.length);
            writeOctal(header, 136, 12, 0);
            Arrays.fill(header, 148, 156, (byte) ' ');
            header[156] = '0';
            writeAscii(header, 257, 6, "ustar");
            writeAscii(header, 263, 2, "00");
            writeChecksum(header);

            output.write(header);
            output.write(content);
            int padding = (int) ((512 - (content.length % 512L)) % 512);
            output.write(new byte[padding]);
            output.write(new byte[1024]);
        }
    }

    private static void writeAscii(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        String padded = "0".repeat(length - octal.length() - 1) + octal;
        writeAscii(header, offset, length - 1, padded);
        header[offset + length - 1] = 0;
    }

    private static void writeChecksum(byte[] header) {
        long checksum = 0;
        for (byte value : header) {
            checksum += value & 0xFF;
        }
        String octal = Long.toOctalString(checksum);
        String padded = "0".repeat(6 - octal.length()) + octal;
        writeAscii(header, 148, 6, padded);
        header[154] = 0;
        header[155] = (byte) ' ';
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
