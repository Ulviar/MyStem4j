package io.github.ulviar.mystem4j.gradle;

import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Configuration of the {@code mystem4j} extension installed by {@link Mystem4jPlugin}.
 *
 * <p>Configure this extension in the build script; Gradle owns its lifecycle. Properties
 * are lazy and are read when the corresponding tasks need them. Applying the plugin
 * alone does not download or run MyStem. Downloads require both {@link #getDownload()}
 * and {@link #getAcceptYandexMystemLicense()} to be enabled.
 *
 * <p>Example in {@code build.gradle.kts}, after applying {@code io.github.ulviar.mystem4j}:
 * <pre>{@code
 * mystem4j {
 *     download.set(true)
 *     acceptYandexMystemLicense.set(true) // After reviewing the Yandex license.
 *     configureTests.set(true)
 * }
 * }</pre>
 *
 * <p>For packaging, wire {@link #getPreparedExecutable()} into a task's file input.
 * It carries the extraction dependency and does not require running a native probe.
 * Use {@link #getExecutablePath()} only when a string path is needed.
 *
 * @see MystemDownloadTask
 * @see MystemProbeTask
 */
public class Mystem4jExtension {
    private final Property<String> version;
    private final Property<Boolean> acceptYandexMystemLicense;
    private final Property<Boolean> download;
    private final Property<Boolean> configureTests;
    private final Property<String> baseUrl;
    private final Property<String> archiveUrl;
    private final Property<String> sha256;
    private final Property<Long> maxArchiveBytes;
    private final Property<Integer> maxProbeOutputBytes;
    private final Property<String> targetOs;
    private final DirectoryProperty cacheDirectory;
    private Provider<RegularFile> downloadedArchive;
    private Provider<RegularFile> preparedExecutable;
    private Provider<String> executablePath;

    /**
     * Creates the configurable properties; the plugin supplies task-backed output providers.
     *
     * @param objects Gradle's injected object factory
     */
    @Inject
    public Mystem4jExtension(ObjectFactory objects) {
        version = objects.property(String.class).convention("3.1");
        acceptYandexMystemLicense = objects.property(Boolean.class).convention(false);
        download = objects.property(Boolean.class).convention(false);
        configureTests = objects.property(Boolean.class).convention(false);
        baseUrl = objects.property(String.class).convention("https://download.cdn.yandex.net/mystem");
        archiveUrl = objects.property(String.class);
        sha256 = objects.property(String.class);
        maxArchiveBytes = objects.property(Long.class).convention(100L * 1024L * 1024L);
        maxProbeOutputBytes = objects.property(Integer.class).convention(64 * 1024);
        targetOs = objects.property(String.class).convention(MystemDistribution.currentOs());
        cacheDirectory = objects.directoryProperty();
    }

    /**
     * Returns the native MyStem version, independent of the MyStem4j library version.
     * Only {@code 3.1} is supported, including when {@link #getArchiveUrl()} is overridden.
     * This is also the default.
     *
     * @return native distribution version property
     */
    public Property<String> getVersion() {
        return version;
    }

    /**
     * Returns explicit acceptance of the
     * <a href="https://yandex.ru/legal/mystem/ru/">Yandex MyStem license</a>.
     * Defaults to {@code false}; set it only after reviewing and accepting the license.
     * This flag does not enable downloading by itself.
     *
     * @return license acceptance property
     */
    public Property<Boolean> getAcceptYandexMystemLicense() {
        return acceptYandexMystemLicense;
    }

    /**
     * Returns the download opt-in, defaulting to {@code false}.
     * The download task fails when executed without this opt-in or license acceptance,
     * including when it could reuse an archive from its own cache.
     *
     * @return download permission property
     * @see #getAcceptYandexMystemLicense()
     */
    public Property<Boolean> getDownload() {
        return download;
    }

    /**
     * Returns whether every Gradle {@link org.gradle.api.tasks.testing.Test} task
     * should depend on {@code mystemPrepareTestRuntime} and receive the executable through
     * the JVM property {@code mystem4j.executable} and environment variable {@code MYSTEM_PATH}.
     * Defaults to {@code false}. Enabling it also requires download and license opt-ins;
     * the prepared binary must be runnable on the build host because preparation includes a probe.
     *
     * @return test integration property
     */
    public Property<Boolean> getConfigureTests() {
        return configureTests;
    }

    /**
     * Returns the base URL to which the selected distribution's archive name is appended.
     * Defaults to {@code https://download.cdn.yandex.net/mystem}. Changing this to a mirror
     * retains the built-in checksum; {@link #getArchiveUrl()} takes precedence when set.
     *
     * @return distribution base URL property
     */
    public Property<String> getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns an optional complete archive URL override, unset by default.
     * Supported schemes are {@code https}, {@code http}, and {@code file}. A custom remote
     * URL requires an explicit {@link #getSha256()}; the built-in checksum is not assumed
     * for an overridden URL. Archive format and executable name still come from the target OS.
     *
     * @return archive URL override property
     */
    public Property<String> getArchiveUrl() {
        return archiveUrl;
    }

    /**
     * Returns the optional expected archive SHA-256 override.
     * When unset, official distributions use their built-in checksum; custom remote
     * archive URLs must supply one. Values are 64 hexadecimal digits, case insensitive,
     * with surrounding whitespace ignored. A local {@code file:} archive may omit it.
     *
     * @return checksum override property
     */
    public Property<String> getSha256() {
        return sha256;
    }

    /**
     * Returns the positive download size limit in bytes, defaulting to
     * {@code 104857600} (100 MiB). It bounds newly transferred archive data, not extracted
     * files. Reusing an already verified cached archive does not reapply the transfer limit.
     *
     * @return archive transfer limit property
     */
    public Property<Long> getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    /**
     * Returns the positive probe capture limit, defaulting to {@code 65536} bytes
     * (64 KiB). The limit applies separately to stdout and stderr; exceeding either fails the probe.
     *
     * @return per-stream probe output limit property
     */
    public Property<Integer> getMaxProbeOutputBytes() {
        return maxProbeOutputBytes;
    }

    /**
     * Returns the target distribution OS, defaulting to the detected host OS.
     * Use {@code linux}, {@code macos}, or {@code windows}. Built-in archives contain Intel
     * binaries; the macOS archive requires Rosetta on Apple Silicon. Selecting another OS
     * allows staging its executable, but does not make that executable runnable on the host.
     *
     * @return target OS property
     */
    public Property<String> getTargetOs() {
        return targetOs;
    }

    /**
     * Returns the shared cache directory, set by the plugin to
     * {@code caches/mystem4j} under Gradle user home. Archives with an expected checksum
     * can be shared across builds and are verified before reuse. Project-local outputs
     * remain under the project's build directory.
     *
     * @return shared checksummed archive cache directory
     */
    public DirectoryProperty getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Returns the archive output of {@code mystemDownload}.
     * Using this provider as a task file input carries the download task dependency;
     * reading the provider alone does not execute the task or guarantee that the file exists.
     *
     * @return lazy task-backed archive provider
     * @throws IllegalStateException if the extension has not been wired by the plugin
     */
    public Provider<RegularFile> getDownloadedArchive() {
        return requireConfigured(downloadedArchive, "downloadedArchive");
    }

    /**
     * Returns the executable output of {@code mystemExtract}.
     * Use it as the source of a {@link org.gradle.api.tasks.Copy} or
     * {@link org.gradle.api.tasks.Sync} task to run download and extraction automatically.
     * It does not depend on {@code mystemProbe}, so staging for another target OS is supported.
     * Reading the provider alone does not execute preparation.
     *
     * @return lazy task-backed executable provider
     * @throws IllegalStateException if the extension has not been wired by the plugin
     */
    public Provider<RegularFile> getPreparedExecutable() {
        return requireConfigured(preparedExecutable, "preparedExecutable");
    }

    /**
     * Returns the absolute path at which extraction will place the executable.
     * This string provider does not carry a preparation task dependency and does not
     * assert that the file exists. Use {@link #getPreparedExecutable()} as a task's file
     * input when preparation must happen before consumption.
     *
     * @return lazy absolute executable path
     * @throws IllegalStateException if the extension has not been wired by the plugin
     */
    public Provider<String> getExecutablePath() {
        return requireConfigured(executablePath, "executablePath");
    }

    void setDownloadedArchive(Provider<RegularFile> downloadedArchive) {
        this.downloadedArchive = downloadedArchive;
    }

    void setPreparedExecutable(Provider<RegularFile> preparedExecutable) {
        this.preparedExecutable = preparedExecutable;
    }

    void setExecutablePath(Provider<String> executablePath) {
        this.executablePath = executablePath;
    }

    private static <T> Provider<T> requireConfigured(Provider<T> provider, String name) {
        if (provider == null) {
            throw new IllegalStateException("MyStem4j Gradle plugin did not configure " + name + ".");
        }
        return provider;
    }
}
