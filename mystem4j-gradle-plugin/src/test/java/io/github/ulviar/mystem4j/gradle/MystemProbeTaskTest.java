package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
        task.getExecutableFile().set(FakeMystemExecutable.create(temporaryDirectory, "not-mystem", "notMystem").toFile());
        task.getTimeoutSeconds().set(5);
        task.getSmokeInput().set("мама");
        task.getMaxOutputBytes().set(1024);
        task.getMarkerFile().set(temporaryDirectory.resolve("probe/not-mystem.properties").toFile());

        assertThrows(GradleException.class, task::probe);
    }

    @Test
    void acceptsMystemLikeJsonProbeOutput() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemProbeTask task = project.getTasks().register("mystemProbeSuccess", MystemProbeTask.class).get();
        task.getExecutableFile().set(FakeMystemExecutable.create(temporaryDirectory, "mystem-success", "echo").toFile());
        task.getTimeoutSeconds().set(5);
        task.getSmokeInput().set("мама");
        task.getMaxOutputBytes().set(1024);
        Path marker = temporaryDirectory.resolve("probe/success.properties");
        task.getMarkerFile().set(marker.toFile());

        task.probe();

        assertTrue(Files.isRegularFile(marker));
    }

    @Test
    void rejectsNonZeroExitCode() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemProbeTask task = project.getTasks().register("mystemProbeFailure", MystemProbeTask.class).get();
        task.getExecutableFile().set(FakeMystemExecutable.create(temporaryDirectory, "mystem-failure", "fail", "7", "bad mystem").toFile());
        task.getTimeoutSeconds().set(5);
        task.getSmokeInput().set("мама");
        task.getMaxOutputBytes().set(1024);
        task.getMarkerFile().set(temporaryDirectory.resolve("probe/failure.properties").toFile());

        assertThrows(GradleException.class, task::probe);
    }

    @Test
    void rejectsOutputLargerThanLimit() throws IOException {
        Project project = ProjectBuilder.builder().build();
        MystemProbeTask task = project.getTasks().register("mystemProbeLargeOutput", MystemProbeTask.class).get();
        task.getExecutableFile().set(FakeMystemExecutable.create(temporaryDirectory, "mystem-large-output", "largeOutput").toFile());
        task.getTimeoutSeconds().set(5);
        task.getSmokeInput().set("мама");
        task.getMaxOutputBytes().set(4);
        task.getMarkerFile().set(temporaryDirectory.resolve("probe/large-output.properties").toFile());

        assertThrows(GradleException.class, task::probe);
    }
}
