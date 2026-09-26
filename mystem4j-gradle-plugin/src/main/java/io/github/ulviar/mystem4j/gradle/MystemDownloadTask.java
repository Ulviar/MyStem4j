package io.github.ulviar.mystem4j.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Downloads or reuses a native MyStem archive after explicit opt-in and license acceptance.
 *
 * <p>{@link Mystem4jPlugin} registers this as {@code mystemDownload} and supplies
 * properties from {@link Mystem4jExtension}. Remote archives require a SHA-256 checksum;
 * new transfers are bounded by {@link #getMaxArchiveBytes()}. HTTP(S) I/O failures are
 * retried up to three total attempts; checksum failures are reported immediately.
 *
 * <p>Project-local reuse checks the archive and metadata. With an expected checksum,
 * a shared cache can also be reused after content verification. New archive data is
 * staged in a temporary file before replacing the output. This task does not extract
 * or execute the binary.
 *
 * @see MystemExtractTask
 */
@DisableCachingByDefault(because = "Downloads an external MyStem archive and manages its own local reuse checks.")
public abstract class MystemDownloadTask extends DefaultTask {
    /**
     * Creates a Gradle-managed task whose properties are configured by the plugin or build script.
     */
    public MystemDownloadTask() {}

    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final ConcurrentMap<Path, Object> LOCAL_LOCKS = new ConcurrentHashMap<>();

    /**
     * Returns the version recorded in reuse metadata and used in the shared cache path.
     *
     * @return native MyStem version, supplied by the extension
     */
    @Input
    public abstract Property<String> getVersion();

    /**
     * Returns whether the task is explicitly allowed to obtain an archive.
     *
     * @return required download opt-in; the plugin defaults it to {@code false}
     */
    @Input
    public abstract Property<Boolean> getDownload();

    /**
     * Returns whether the build has explicitly accepted the Yandex MyStem license.
     *
     * @return required license opt-in; the plugin defaults it to {@code false}
     */
    @Input
    public abstract Property<Boolean> getAcceptYandexMystemLicense();

    /**
     * Returns the complete archive URL using {@code https}, {@code http}, or {@code file}.
     *
     * @return source URL property
     */
    @Input
    public abstract Property<String> getArchiveUrl();

    /**
     * Returns the expected SHA-256 as 64 hexadecimal digits, ignoring case and surrounding whitespace.
     * Unset or blank is allowed only for {@code file:} URLs and disables shared-cache reuse.
     *
     * @return expected checksum property
     */
    @Input
    @Optional
    public abstract Property<String> getExpectedSha256();

    /**
     * Returns the positive byte limit for new transfers, checked against reported size and bytes read.
     * The limit does not apply to extracted data or to previously verified cache entries.
     *
     * @return transfer limit; the plugin defaults it to 100 MiB
     */
    @Input
    public abstract Property<Long> getMaxArchiveBytes();

    /**
     * Returns the shared cache root used when a checksum is available.
     * This optimization is independent of the declared project-local archive output.
     *
     * @return shared cache directory
     */
    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    /**
     * Returns the project-local destination consumed by extraction tasks.
     *
     * @return archive output file
     */
    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    /**
     * Returns the sidecar recording version, source URL, and expected checksum for local reuse.
     *
     * @return metadata output file
     */
    @OutputFile
    public abstract RegularFileProperty getMetadataFile();

    /**
     * Validates opt-ins and settings, then reuses or transfers the archive and writes metadata.
     *
     * @throws GradleException if opt-ins are absent, settings are invalid, a transfer exceeds
     *         its limit, checksum verification fails, or archive I/O fails
     * @throws IllegalArgumentException if the archive URL is not a syntactically valid URI
     */
    @TaskAction
    public void download() {
        if (!getDownload().get()) {
            throw new GradleException(
                    "MyStem download is disabled. Set mystem4j { download.set(true) } to download MyStem.");
        }
        if (!getAcceptYandexMystemLicense().get()) {
            throw new GradleException(
                    "MyStem download requires explicit license acceptance. Set mystem4j { acceptYandexMystemLicense.set(true) } after reviewing https://yandex.ru/legal/mystem/ru/.");
        }

        Path archive = getArchiveFile().get().getAsFile().toPath();
        Path metadata = getMetadataFile().get().getAsFile().toPath();
        String expectedSha256 = normalizeSha256(getExpectedSha256().getOrNull());
        long maxArchiveBytes = getMaxArchiveBytes().get();
        URI archiveUri = URI.create(getArchiveUrl().get());
        validateDownloadRequest(archiveUri, expectedSha256, maxArchiveBytes);
        if (Files.exists(archive)
                && metadataMatches(metadata, getVersion().get(), getArchiveUrl().get(), expectedSha256)
                && matchesExpectedSha256(archive, expectedSha256)) {
            getLogger().lifecycle("Using cached MyStem archive: {}", archive);
            return;
        }

        try {
            Files.createDirectories(archive.getParent());
            Files.createDirectories(metadata.getParent());
            Path temporaryArchive = archive.resolveSibling(archive.getFileName() + ".part");
            Files.deleteIfExists(temporaryArchive);
            materializeArchive(
                    archiveUri,
                    temporaryArchive,
                    archive.getFileName().toString(),
                    maxArchiveBytes,
                    expectedSha256);
            if (!matchesExpectedSha256(temporaryArchive, expectedSha256)) {
                Files.deleteIfExists(temporaryArchive);
                throw new GradleException("Downloaded MyStem archive checksum does not match expected sha256.");
            }
            moveReplacing(temporaryArchive, archive);
            writeMetadata(metadata, getVersion().get(), getArchiveUrl().get(), expectedSha256);
            getLogger().lifecycle("Downloaded MyStem {} to {}", getVersion().get(), archive);
        } catch (GradleException error) {
            try {
                Files.deleteIfExists(archive.resolveSibling(archive.getFileName() + ".part"));
            } catch (IOException ignored) {
                error.addSuppressed(ignored);
            }
            throw error;
        } catch (IOException error) {
            try {
                Files.deleteIfExists(archive.resolveSibling(archive.getFileName() + ".part"));
            } catch (IOException ignored) {
                error.addSuppressed(ignored);
            }
            throw new GradleException("Failed to download MyStem archive from " + getArchiveUrl().get(), error);
        }
    }

    @SuppressWarnings("try")
    private void materializeArchive(
            URI archiveUri, Path destination, String archiveName, long maxArchiveBytes, String expectedSha256)
            throws IOException {
        if (expectedSha256.isBlank()) {
            downloadTo(archiveUri, destination, maxArchiveBytes);
            return;
        }

        Path cacheRoot = getCacheDirectory().get().getAsFile().toPath();
        Path cachedArchive = cacheRoot.resolve(getVersion().get()).resolve(expectedSha256).resolve(archiveName);
        Path lockFile = cachedArchive.resolveSibling(cachedArchive.getFileName() + ".lock");
        Path temporaryCacheArchive = cachedArchive.resolveSibling(cachedArchive.getFileName() + ".part");
        Files.createDirectories(cachedArchive.getParent());
        Object localLock = LOCAL_LOCKS.computeIfAbsent(lockFile.toAbsolutePath().normalize(), ignored -> new Object());
        synchronized (localLock) {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                if (!Files.isRegularFile(cachedArchive) || !matchesExpectedSha256(cachedArchive, expectedSha256)) {
                    Files.deleteIfExists(temporaryCacheArchive);
                    try {
                        downloadTo(archiveUri, temporaryCacheArchive, maxArchiveBytes);
                        if (!matchesExpectedSha256(temporaryCacheArchive, expectedSha256)) {
                            throw new GradleException(
                                    "Downloaded MyStem archive checksum does not match expected sha256.");
                        }
                        moveReplacing(temporaryCacheArchive, cachedArchive);
                    } catch (GradleException | IOException error) {
                        try {
                            Files.deleteIfExists(temporaryCacheArchive);
                        } catch (IOException cleanupError) {
                            error.addSuppressed(cleanupError);
                        }
                        throw error;
                    }
                }
                Files.copy(cachedArchive, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void validateDownloadRequest(URI uri, String expectedSha256, long maxArchiveBytes) {
        if (maxArchiveBytes <= 0) {
            throw new GradleException("maxArchiveBytes must be positive.");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new GradleException("MyStem archive URL must include a scheme: " + uri);
        }
        String normalizedScheme = scheme.toLowerCase(java.util.Locale.ROOT);
        if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme) && !"file".equals(normalizedScheme)) {
            throw new GradleException("Unsupported MyStem archive URL scheme: " + scheme);
        }
        if (("https".equals(normalizedScheme) || "http".equals(normalizedScheme))
                && normalizeSha256(expectedSha256).isBlank()) {
            throw new GradleException("Remote MyStem downloads require an expected sha256 checksum.");
        }
    }

    private static void downloadTo(URI uri, Path destination, long maxArchiveBytes) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadOnce(uri, destination, maxArchiveBytes);
                return;
            } catch (IOException error) {
                Files.deleteIfExists(destination);
                lastError = error;
                if (!isRemote(uri) || attempt == MAX_DOWNLOAD_ATTEMPTS) {
                    throw error;
                }
                sleepBeforeRetry(error);
            }
        }
        throw lastError;
    }

    private static void downloadOnce(URI uri, Path destination, long maxArchiveBytes) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        long contentLength = connection.getContentLengthLong();
        if (contentLength > maxArchiveBytes) {
            throw new GradleException("MyStem archive is larger than maxArchiveBytes: " + contentLength);
        }
        try (InputStream input = connection.getInputStream()) {
            copyWithLimit(input, destination, maxArchiveBytes);
        }
    }

    private static boolean isRemote(URI uri) {
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    private static void sleepBeforeRetry(IOException error) throws IOException {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            IOException interruptedError = new IOException("Interrupted while retrying MyStem archive download.", interrupted);
            interruptedError.addSuppressed(error);
            throw interruptedError;
        }
    }

    private static void copyWithLimit(InputStream input, Path destination, long maxArchiveBytes) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        try (OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxArchiveBytes) {
                    throw new GradleException("MyStem archive exceeded maxArchiveBytes: " + maxArchiveBytes);
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static boolean metadataMatches(Path metadata, String version, String archiveUrl, String expectedSha256) {
        if (!Files.isRegularFile(metadata)) {
            return false;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException error) {
            return false;
        }
        return version.equals(properties.getProperty("version"))
                && archiveUrl.equals(properties.getProperty("archiveUrl"))
                && normalizeSha256(expectedSha256).equals(properties.getProperty("expectedSha256", ""));
    }

    private static void writeMetadata(Path metadata, String version, String archiveUrl, String expectedSha256)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", version);
        properties.setProperty("archiveUrl", archiveUrl);
        properties.setProperty("expectedSha256", normalizeSha256(expectedSha256));
        try (Writer writer = Files.newBufferedWriter(metadata, StandardCharsets.UTF_8)) {
            properties.store(writer, "Generated by mystemDownload");
        }
    }

    private static String normalizeSha256(String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return "";
        }
        String normalized = expectedSha256.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new GradleException("expectedSha256 must be a 64-character lowercase or uppercase hex SHA-256 value.");
        }
        return normalized;
    }

    private static boolean matchesExpectedSha256(Path file, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return true;
        }
        return normalizeSha256(expectedSha256).equals(sha256(file));
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException error) {
            throw new GradleException("Failed to read " + file + " while calculating sha256.", error);
        } catch (NoSuchAlgorithmException error) {
            throw new GradleException("Current JDK does not provide SHA-256.", error);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
