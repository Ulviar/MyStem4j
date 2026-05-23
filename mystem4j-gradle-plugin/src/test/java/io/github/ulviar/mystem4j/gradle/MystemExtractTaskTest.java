package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemExtractTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsExecutableFromZipArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("mystem.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("mystem/bin/mystem"));
            zip.write("fake mystem".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Project project = ProjectBuilder.builder().build();
        MystemExtractTask task = project.getTasks().create("mystemExtract", MystemExtractTask.class);
        Path executable = temporaryDirectory.resolve("prepared/mystem");
        task.getArchiveFile().set(archive.toFile());
        task.getArchiveType().set("zip");
        task.getExecutableName().set("mystem");
        task.getExecutableFile().set(executable.toFile());

        task.extract();

        assertTrue(Files.exists(executable));
        assertEquals("fake mystem", Files.readString(executable, StandardCharsets.UTF_8));
    }
}
