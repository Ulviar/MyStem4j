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

/**
 * Extracts a MyStem executable from a ZIP or gzip-compressed TAR archive.
 *
 * <p>The plugin registers this task as {@code mystemExtract}, depending on
 * {@code mystemDownload}. It locates the configured executable name in the unpacked
 * archive, copies it to the declared output, and attempts to mark it executable.
 * It does not start the binary; use {@link MystemProbeTask} to verify host compatibility.
 * This separation permits packaging an executable for another OS.
 *
 * @see Mystem4jExtension#getPreparedExecutable()
 */
@DisableCachingByDefault(because = "Extracts and marks a platform executable in the local build directory.")
public abstract class MystemExtractTask extends DefaultTask {
    private final ArchiveOperations archiveOperations;
    private final FileSystemOperations fileSystemOperations;

    /**
     * Creates the task with Gradle-managed archive and filesystem services.
     *
     * @param archiveOperations archive readers
     * @param fileSystemOperations file copy service
     */
    @Inject
    public MystemExtractTask(ArchiveOperations archiveOperations, FileSystemOperations fileSystemOperations) {
        this.archiveOperations = archiveOperations;
        this.fileSystemOperations = fileSystemOperations;
    }

    /**
     * Returns the input archive, normally the output of {@code mystemDownload}.
     *
     * @return archive input file
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /**
     * Returns the archive format: {@code zip} or {@code tar.gz}.
     *
     * @return archive format property
     */
    @Input
    public abstract Property<String> getArchiveType();

    /**
     * Returns the executable basename to locate, including inside nested archive directories.
     * Official archives use {@code mystem} or {@code mystem.exe} according to the target OS.
     *
     * @return expected executable filename
     */
    @Input
    public abstract Property<String> getExecutableName();

    /**
     * Returns the destination for the selected executable; an existing file is replaced.
     *
     * @return executable output file
     */
    @OutputFile
    public abstract RegularFileProperty getExecutableFile();

    /**
     * Unpacks the archive and copies the matching executable to the declared output.
     *
     * @throws GradleException if the format is unsupported, the executable is absent,
     *         or extraction or file I/O fails
     */
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
