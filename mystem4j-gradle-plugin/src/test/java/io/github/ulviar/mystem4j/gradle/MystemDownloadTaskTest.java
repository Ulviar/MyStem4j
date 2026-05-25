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
        task.getArchiveFile().set(archive.toFile());
        task.getMetadataFile().set(temporaryDirectory.resolve("mystem.tar.gz.metadata.properties").toFile());

        task.download();
        assertEquals("first", Files.readString(archive, StandardCharsets.UTF_8));

        task.getArchiveUrl().set(secondSource.toUri().toString());
        task.download();

        assertEquals("second", Files.readString(archive, StandardCharsets.UTF_8));
    }
}
