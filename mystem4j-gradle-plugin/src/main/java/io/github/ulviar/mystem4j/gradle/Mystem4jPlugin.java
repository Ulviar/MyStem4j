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
        extension.getDistributionDirectory().convention(project.getLayout().getBuildDirectory().dir("mystem/distribution"));

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

        project.getTasks()
                .register("mystemPrepareDistribution", MystemPrepareDistributionTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Copies the prepared MyStem executable into a distribution directory.");
                    task.dependsOn(probe);
                    task.getPrepareDistribution().set(extension.getPrepareDistribution());
                    task.getExecutableFile().set(extract.flatMap(MystemExtractTask::getExecutableFile));
                    task.getDistributionDirectory().set(extension.getDistributionDirectory());
        });

        Provider<String> preparedExecutable = executableFile.map(file -> file.getAsFile().getAbsolutePath());
        Provider<String> configuredExecutable =
                project.provider(() -> extension.getConfigureTests().get() ? preparedExecutable.get() : null);
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.dependsOn(project.provider(() -> extension.getConfigureTests().get() ? prepareTestRuntime.get() : null));
            test.getInputs().property(EXECUTABLE_PROPERTY, configuredExecutable).optional(true);
            test.getInputs().file(executableFile).optional();
            test.systemProperty(EXECUTABLE_PROPERTY, configuredExecutable);
            test.environment(EXECUTABLE_ENV, configuredExecutable);
        });
    }
}
