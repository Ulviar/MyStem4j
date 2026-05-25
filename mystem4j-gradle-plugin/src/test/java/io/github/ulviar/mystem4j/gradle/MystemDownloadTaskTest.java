package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemDownloadTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsDownloadWithoutExplicitOptIn() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task = project.getTasks().register("mystemDownload", MystemDownloadTask.class).get();
        task.getVersion().set("3.1");
        task.getDownload().set(false);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set("https://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz");
        task.getExpectedSha256().set("4696f4ea8ce3ecda24ef5e8dfe7e4b16cfa5f1844edfcca31c34d636b73c0a62");
        task.getMaxArchiveBytes().set(1024L * 1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(temporaryDirectory.resolve("mystem.tar.gz").toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    @Test
    void rejectsDownloadWithoutLicenseAcceptance() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithLicenseCheck", MystemDownloadTask.class).get();
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(false);
        task.getArchiveUrl().set("https://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz");
        task.getExpectedSha256().set("4696f4ea8ce3ecda24ef5e8dfe7e4b16cfa5f1844edfcca31c34d636b73c0a62");
        task.getMaxArchiveBytes().set(1024L * 1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(temporaryDirectory.resolve("mystem.tar.gz").toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    @Test
    void redownloadsWhenArchiveMetadataChanges() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithMetadata", MystemDownloadTask.class).get();
        Path firstSource = temporaryDirectory.resolve("first.tar.gz");
        Path secondSource = temporaryDirectory.resolve("second.tar.gz");
        Path archive = temporaryDirectory.resolve("mystem.tar.gz");
        Files.writeString(firstSource, "first", StandardCharsets.UTF_8);
        Files.writeString(secondSource, "second", StandardCharsets.UTF_8);
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set(firstSource.toUri().toString());
        task.getMaxArchiveBytes().set(1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        task.download();
        assertEquals("first", Files.readString(archive, StandardCharsets.UTF_8));

        task.getArchiveUrl().set(secondSource.toUri().toString());
        task.download();

        assertEquals("second", Files.readString(archive, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsRemoteDownloadWithoutChecksum() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithoutChecksum", MystemDownloadTask.class).get();
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set("https://example.com/mystem.tar.gz");
        task.getMaxArchiveBytes().set(1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(temporaryDirectory.resolve("mystem.tar.gz").toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    @Test
    void rejectsChecksumMismatch() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithChecksumMismatch", MystemDownloadTask.class).get();
        Path source = temporaryDirectory.resolve("source.tar.gz");
        Path archive = temporaryDirectory.resolve("mystem.tar.gz");
        Files.writeString(source, "content", StandardCharsets.UTF_8);
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set(source.toUri().toString());
        task.getExpectedSha256().set("0000000000000000000000000000000000000000000000000000000000000000");
        task.getMaxArchiveBytes().set(1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    @Test
    void reusesChecksummedArchiveFromSharedCache() throws IOException {
        Project project = ProjectBuilder.builder().build();
        Path source = temporaryDirectory.resolve("source.tar.gz");
        Path firstArchive = temporaryDirectory.resolve("first/mystem.tar.gz");
        Path secondArchive = temporaryDirectory.resolve("second/mystem.tar.gz");
        Path cache = temporaryDirectory.resolve("cache");
        Files.createDirectories(firstArchive.getParent());
        Files.createDirectories(secondArchive.getParent());
        Files.writeString(source, "first", StandardCharsets.UTF_8);
        String checksum = sha256(source);

        MystemDownloadTask firstTask =
                project.getTasks().register("firstChecksummedDownload", MystemDownloadTask.class).get();
        configureDownloadTask(firstTask, source, checksum, cache, firstArchive);
        firstTask.download();
        assertEquals("first", Files.readString(firstArchive, StandardCharsets.UTF_8));

        Files.writeString(source, "second", StandardCharsets.UTF_8);
        MystemDownloadTask secondTask =
                project.getTasks().register("secondChecksummedDownload", MystemDownloadTask.class).get();
        configureDownloadTask(secondTask, source, checksum, cache, secondArchive);
        secondTask.download();

        assertEquals("first", Files.readString(secondArchive, StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.walk(cache)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().equals("mystem.tar.gz")));
        }
    }

    @Test
    void rejectsArchiveLargerThanLimit() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithSmallLimit", MystemDownloadTask.class).get();
        Path source = temporaryDirectory.resolve("source.tar.gz");
        Path archive = temporaryDirectory.resolve("mystem.tar.gz");
        Files.writeString(source, "large", StandardCharsets.UTF_8);
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set(source.toUri().toString());
        task.getMaxArchiveBytes().set(1L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    @Test
    void rejectsUnsupportedUrlScheme() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemDownloadTask task =
                project.getTasks().register("mystemDownloadWithUnsupportedScheme", MystemDownloadTask.class).get();
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set("ftp://example.com/mystem.tar.gz");
        task.getExpectedSha256().set("4696f4ea8ce3ecda24ef5e8dfe7e4b16cfa5f1844edfcca31c34d636b73c0a62");
        task.getMaxArchiveBytes().set(1024L);
        task.getCacheDirectory().set(temporaryDirectory.resolve("cache").toFile());
        task.getArchiveFile().set(temporaryDirectory.resolve("mystem.tar.gz").toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }

    private void configureDownloadTask(
            MystemDownloadTask task, Path source, String checksum, Path cache, Path archive) {
        task.getVersion().set("3.1");
        task.getDownload().set(true);
        task.getAcceptYandexMystemLicense().set(true);
        task.getArchiveUrl().set(source.toUri().toString());
        task.getExpectedSha256().set(checksum);
        task.getMaxArchiveBytes().set(1024L);
        task.getCacheDirectory().set(cache.toFile());
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(archive.resolveSibling(archive.getFileName() + ".metadata.properties").toFile());
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
}
