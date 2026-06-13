package io.github.ulviar.mystem4j.gradle;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

public class Mystem4jPlugin implements Plugin<Project> {
    static final String GROUP = "mystem4j";
    static final String EXECUTABLE_PROPERTY = "mystem4j.executable";
    static final String EXECUTABLE_ENV = "MYSTEM_PATH";

    @Override
    public void apply(Project project) {
        Mystem4jExtension extension =
                project.getExtensions().create("mystem4j", Mystem4jExtension.class);
        extension.getCacheDirectory().convention(project.getLayout()
                .dir(project.provider(() -> project.getGradle()
                        .getGradleUserHomeDir()
                        .toPath()
                        .resolve("caches/mystem4j")
                        .toFile())));

        Provider<MystemDistribution> distribution = project.provider(
                () -> MystemDistribution.forOs(extension.getTargetOs().get(), extension.getVersion().get()));
        Provider<String> archiveUrl = project.provider(() -> extension.getArchiveUrl().isPresent()
                ? extension.getArchiveUrl().get()
                : distribution.get().archiveUrl(extension.getBaseUrl().get()));
        Provider<String> expectedSha256 = project.provider(() -> {
            if (extension.getSha256().isPresent()) {
                return extension.getSha256().get();
            }
            if (extension.getArchiveUrl().isPresent()) {
                return "";
            }
            return distribution.get().sha256();
        });
        Provider<RegularFile> archiveFile = project.getLayout()
                .getBuildDirectory()
                .file(distribution.map(value -> "mystem/downloads/" + value.archiveName()));
        Provider<RegularFile> metadataFile = project.getLayout()
                .getBuildDirectory()
                .file(distribution.map(value -> "mystem/downloads/" + value.archiveName() + ".metadata.properties"));
        Provider<RegularFile> executableFile = project.getLayout()
                .getBuildDirectory()
                .file(distribution.map(value -> "mystem/bin/" + value.platformId() + "/" + value.executableName()));

        TaskProvider<MystemDownloadTask> download = project.getTasks()
                .register("mystemDownload", MystemDownloadTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Downloads the MyStem archive for the configured platform.");
                    task.getVersion().set(extension.getVersion());
                    task.getDownload().set(extension.getDownload());
                    task.getAcceptYandexMystemLicense().set(extension.getAcceptYandexMystemLicense());
                    task.getArchiveUrl().set(archiveUrl);
                    task.getExpectedSha256().set(expectedSha256);
                    task.getMaxArchiveBytes().set(extension.getMaxArchiveBytes());
                    task.getCacheDirectory().set(extension.getCacheDirectory());
                    task.getArchiveFile().set(archiveFile);
                    task.getMetadataFile().set(metadataFile);
                });

        TaskProvider<MystemExtractTask> extract = project.getTasks()
                .register("mystemExtract", MystemExtractTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Extracts the MyStem executable from the downloaded archive.");
                    task.dependsOn(download);
                    task.getArchiveFile().set(download.flatMap(MystemDownloadTask::getArchiveFile));
                    task.getArchiveType().set(distribution.map(MystemDistribution::archiveType));
                    task.getExecutableName().set(distribution.map(MystemDistribution::executableName));
                    task.getExecutableFile().set(executableFile);
                });

        TaskProvider<MystemProbeTask> probe = project.getTasks()
                .register("mystemProbe", MystemProbeTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Runs a smoke request against the prepared MyStem executable.");
                    task.dependsOn(extract);
                    task.getExecutableFile().set(extract.flatMap(MystemExtractTask::getExecutableFile));
                    task.getTimeoutSeconds().convention(10);
                    task.getSmokeInput().convention("мама");
                    task.getMaxOutputBytes().set(extension.getMaxProbeOutputBytes());
                    task.getMarkerFile().set(project.getLayout()
                            .getBuildDirectory()
                            .file(distribution.map(value -> "mystem/probe/" + value.platformId() + ".properties")));
                });

        Provider<RegularFile> downloadedArchive = download.flatMap(MystemDownloadTask::getArchiveFile);
        Provider<RegularFile> preparedExecutableFile = extract.flatMap(MystemExtractTask::getExecutableFile);
        Provider<String> preparedExecutablePath = executableFile.map(file -> file.getAsFile().getAbsolutePath());
        extension.setDownloadedArchive(downloadedArchive);
        extension.setPreparedExecutable(preparedExecutableFile);
        extension.setExecutablePath(preparedExecutablePath);

        TaskProvider<MystemPrepareRuntimeTask> prepareTestRuntime = project.getTasks()
                .register("mystemPrepareTestRuntime", MystemPrepareRuntimeTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Prepares the MyStem executable path for Gradle Test tasks.");
                    task.dependsOn(probe);
                    task.getVersion().set(extension.getVersion());
                    task.getExecutableFile().set(extract.flatMap(MystemExtractTask::getExecutableFile));
                    task.getPropertiesFile().set(project.getLayout()
                            .getBuildDirectory()
                            .file("mystem/mystem4j-test-runtime.properties"));
                });
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.dependsOn(project.provider(
                    () -> extension.getConfigureTests().get() ? List.of(prepareTestRuntime) : List.of()));
            test.getInputs().property("mystem4j.configureTests", extension.getConfigureTests());
            test.getInputs()
                    .files(project.provider(() -> extension.getConfigureTests().get()
                            ? project.files(executableFile)
                            : project.files()))
                    .withPropertyName("mystem4j.executableFile")
                    .optional();
            test.getJvmArgumentProviders()
                    .add(new MystemExecutableArgumentProvider(extension.getConfigureTests(), preparedExecutablePath));
            test.doFirst(task -> {
                if (extension.getConfigureTests().get()) {
                    test.environment(EXECUTABLE_ENV, preparedExecutablePath.get());
                }
            });
        });
    }

    private static final class MystemExecutableArgumentProvider implements CommandLineArgumentProvider {
        private final Property<Boolean> configureTests;
        private final Provider<String> executablePath;

        private MystemExecutableArgumentProvider(Property<Boolean> configureTests, Provider<String> executablePath) {
            this.configureTests = configureTests;
            this.executablePath = executablePath;
        }

        @Input
        public boolean isConfigureTests() {
            return configureTests.get();
        }

        @Input
        @Optional
        public String getExecutablePath() {
            return isConfigureTests() ? executablePath.get() : null;
        }

        @Override
        public Iterable<String> asArguments() {
            String path = getExecutablePath();
            if (path == null) {
                return List.of();
            }
            return List.of("-D" + EXECUTABLE_PROPERTY + "=" + path);
        }
    }
}
