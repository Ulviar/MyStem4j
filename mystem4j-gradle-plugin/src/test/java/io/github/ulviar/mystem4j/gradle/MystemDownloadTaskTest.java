package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
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
        task.getArchiveFile().set(temporaryDirectory.resolve("mystem.tar.gz").toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        assertThrows(GradleException.class, task::download);
    }
}
