package io.github.ulviar.mystem4j.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Extracts and marks a platform executable in the local build directory.")
public abstract class MystemExtractTask extends DefaultTask {
    private final ArchiveOperations archiveOperations;
    private final FileSystemOperations fileSystemOperations;

    @Inject
    public MystemExtractTask(ArchiveOperations archiveOperations, FileSystemOperations fileSystemOperations) {
        this.archiveOperations = archiveOperations;
        this.fileSystemOperations = fileSystemOperations;
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    @Input
    public abstract Property<String> getArchiveType();

    @Input
    public abstract Property<String> getExecutableName();

    @OutputFile
    public abstract RegularFileProperty getExecutableFile();

    @TaskAction
    public void extract() {
        File archive = getArchiveFile().get().getAsFile();
        File temporaryDirectory = getTemporaryDir();
        Path temporaryRoot = temporaryDirectory.toPath();
        Path executable = getExecutableFile().get().getAsFile().toPath();

        try {
            deleteRecursively(temporaryRoot);
            Files.createDirectories(temporaryRoot);
            FileTree tree = archiveTree(archive);
            fileSystemOperations.copy(copy -> {
                copy.from(tree);
                copy.into(temporaryDirectory);
            });

            Path extractedExecutable = findExecutable(temporaryRoot, getExecutableName().get());
            Files.createDirectories(executable.getParent());
            Files.copy(extractedExecutable, executable, StandardCopyOption.REPLACE_EXISTING);
            executable.toFile().setExecutable(true, false);
            getLogger().lifecycle("Prepared MyStem executable: {}", executable);
        } catch (IOException error) {
            throw new GradleException("Failed to extract MyStem executable from " + archive, error);
        }
    }

    private FileTree archiveTree(File archive) {
        return switch (getArchiveType().get()) {
            case "zip" -> archiveOperations.zipTree(archive);
            case "tar.gz" -> archiveOperations.tarTree(archiveOperations.gzip(archive));
            default -> throw new GradleException("Unsupported MyStem archive type: " + getArchiveType().get());
        };
    }

    private static Path findExecutable(Path root, String executableName) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> executableName.equals(path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new GradleException(
                            "MyStem archive does not contain expected executable " + executableName));
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
