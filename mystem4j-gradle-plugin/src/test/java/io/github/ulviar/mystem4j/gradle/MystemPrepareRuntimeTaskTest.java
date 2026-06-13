package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemPrepareRuntimeTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesRuntimePropertiesWithExecutablePathAndVersion() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemPrepareRuntimeTask task =
                project.getTasks().register("mystemPrepareRuntime", MystemPrepareRuntimeTask.class).get();
        Path executable = temporaryDirectory.resolve("mystem");
        Path propertiesFile = temporaryDirectory.resolve("mystem-runtime.properties");
        Files.writeString(executable, "", StandardCharsets.UTF_8);
        task.getVersion().set("3.1");
        task.getExecutableFile().set(executable.toFile());
        task.getPropertiesFile().set(propertiesFile.toFile());

        task.writeRuntimeProperties();

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals(executable.toAbsolutePath().toString(), properties.getProperty("mystem4j.executable"));
        assertEquals("3.1", properties.getProperty("mystem4j.version"));
    }
}
