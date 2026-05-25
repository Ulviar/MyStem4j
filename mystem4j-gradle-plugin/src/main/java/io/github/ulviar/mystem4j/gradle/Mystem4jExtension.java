package io.github.ulviar.mystem4j.gradle;

import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class Mystem4jExtension {
    private final Property<String> version;
    private final Property<Boolean> acceptYandexMystemLicense;
    private final Property<Boolean> download;
    private final Property<Boolean> configureTests;
    private final Property<Boolean> prepareDistribution;
    private final Property<String> baseUrl;
    private final Property<String> archiveUrl;
    private final Property<String> sha256;
    private final Property<Long> maxArchiveBytes;
    private final Property<Integer> maxProbeOutputBytes;
    private final Property<String> targetOs;
    private final DirectoryProperty distributionDirectory;

    @Inject
    public Mystem4jExtension(ObjectFactory objects) {
        version = objects.property(String.class).convention("3.1");
        acceptYandexMystemLicense = objects.property(Boolean.class).convention(false);
        download = objects.property(Boolean.class).convention(false);
        configureTests = objects.property(Boolean.class).convention(false);
        prepareDistribution = objects.property(Boolean.class).convention(false);
        baseUrl = objects.property(String.class).convention("https://download.cdn.yandex.net/mystem");
        archiveUrl = objects.property(String.class);
        sha256 = objects.property(String.class);
        maxArchiveBytes = objects.property(Long.class).convention(100L * 1024L * 1024L);
        maxProbeOutputBytes = objects.property(Integer.class).convention(64 * 1024);
        targetOs = objects.property(String.class).convention(MystemDistribution.currentOs());
        distributionDirectory = objects.directoryProperty();
    }

    public Property<String> getVersion() {
        return version;
    }

    public Property<Boolean> getAcceptYandexMystemLicense() {
        return acceptYandexMystemLicense;
    }

    public Property<Boolean> getDownload() {
        return download;
    }

    public Property<Boolean> getConfigureTests() {
        return configureTests;
    }

    public Property<Boolean> getPrepareDistribution() {
        return prepareDistribution;
    }

    public Property<String> getBaseUrl() {
        return baseUrl;
    }

    public Property<String> getArchiveUrl() {
        return archiveUrl;
    }

    public Property<String> getSha256() {
        return sha256;
    }

    public Property<Long> getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    public Property<Integer> getMaxProbeOutputBytes() {
        return maxProbeOutputBytes;
    }

    public Property<String> getTargetOs() {
        return targetOs;
    }

    public DirectoryProperty getDistributionDirectory() {
        return distributionDirectory;
    }
}
