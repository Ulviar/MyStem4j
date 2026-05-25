package io.github.ulviar.mystem4j.gradle;

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

class MystemProbeTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsNonMystemProbeOutput() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemProbeTask task = project.getTasks().register("mystemProbe", MystemProbeTask.class).get();
        task.getExecutableFile().set(script(
                        "not-mystem",
                        """
                        #!/bin/sh
                        echo "ready"
                        """)
                .toFile());
        task.getTimeoutSeconds().set(5);
        task.getSmokeInput().set("мама");

        assertThrows(GradleException.class, task::probe);
    }

    private Path script(String name, String body) throws IOException {
        Path executable = temporaryDirectory.resolve(name);
        Files.writeString(executable, body, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        return executable;
    }
}
