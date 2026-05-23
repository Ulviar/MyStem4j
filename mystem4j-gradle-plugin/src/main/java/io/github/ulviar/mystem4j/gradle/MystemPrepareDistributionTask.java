package io.github.ulviar.mystem4j.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Copies a local native executable into an application distribution directory.")
public abstract class MystemPrepareDistributionTask extends DefaultTask {
    @Input
    public abstract Property<Boolean> getPrepareDistribution();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutableFile();

    @OutputDirectory
    public abstract DirectoryProperty getDistributionDirectory();

    @TaskAction
    public void prepareDistribution() {
        if (!getPrepareDistribution().get()) {
            throw new GradleException(
                    "Distribution preparation is disabled. Set mystem4j { prepareDistribution.set(true) } to copy MyStem into the distribution directory.");
        }

        Path executable = getExecutableFile().get().getAsFile().toPath();
        Path distributionDirectory = getDistributionDirectory().get().getAsFile().toPath();
        Path target = distributionDirectory.resolve(executable.getFileName());
        try {
            Files.createDirectories(distributionDirectory);
            Files.copy(executable, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().setExecutable(true, false);
            getLogger().lifecycle("Prepared MyStem distribution executable: {}", target);
        } catch (IOException error) {
            throw new GradleException("Failed to copy MyStem executable to " + distributionDirectory, error);
        }
    }
}
