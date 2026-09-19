package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Compiles one consumer with only its declared library's Gradle dependency variants. */
public abstract class JpmsSmokeTestTask extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getModulePath();

    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSourceFile();

    @Input
    public abstract Property<String> getRequiredModule();

    @OutputDirectory
    public abstract DirectoryProperty getWorkDirectory();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    public abstract Property<String> getJavacExecutable();

    @TaskAction
    public void runSmokeTest() throws IOException {
        Path work = getWorkDirectory().get().getAsFile().toPath();
        if (Files.exists(work)) {
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Path classpathOutput = Files.createDirectories(work.resolve("classpath"));
        Path moduleOutput = Files.createDirectories(work.resolve("modulepath"));
        Path descriptor = work.resolve("module-info.java");
        Files.writeString(descriptor, "module mystem4j.consumer { requires " + getRequiredModule().get() + "; }\n");
        String source = getSourceFile().get().getAsFile().getAbsolutePath();
        String runtimePath = getModulePath().getAsPath();
        String compiler = getJavacExecutable().get();
        String java = getJavaExecutable().get();

        ProcessSupport.run(List.of(
                compiler, "--release", "25", "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                "--class-path", getCompileClasspath().getAsPath(),
                "-d", classpathOutput.toString(), source), work.toFile());
        ProcessSupport.run(List.of(
                java, "--class-path", classpathOutput + File.pathSeparator + runtimePath,
                "consumer.Smoke"), work.toFile());

        ProcessSupport.run(List.of(
                compiler, "--release", "25", "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                "--module-path", runtimePath, "-d", moduleOutput.toString(),
                descriptor.toString(), source), work.toFile());
        ProcessSupport.run(List.of(
                java, "--module-path", moduleOutput + File.pathSeparator + runtimePath,
                "--module", "mystem4j.consumer/consumer.Smoke"), work.toFile());
        getLogger().lifecycle("Verified isolated classpath and JPMS consumer for {}.", getRequiredModule().get());
    }
}
