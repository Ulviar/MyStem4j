package io.github.ulviar.mystem4j.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

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
        Provider<String> configuredExecutable =
                project.provider(() -> extension.getConfigureTests().get() ? preparedExecutablePath.get() : null);
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.dependsOn(project.provider(() -> extension.getConfigureTests().get() ? prepareTestRuntime.get() : null));
            test.getInputs().property(EXECUTABLE_PROPERTY, configuredExecutable).optional(true);
            test.getInputs().file(executableFile).optional();
            test.systemProperty(EXECUTABLE_PROPERTY, configuredExecutable);
            test.environment(EXECUTABLE_ENV, configuredExecutable);
        });
    }
}
