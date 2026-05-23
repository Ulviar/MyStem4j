package io.github.ulviar.mystem4j.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Downloads an external MyStem archive and manages its own local reuse checks.")
public abstract class MystemDownloadTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<Boolean> getDownload();

    @Input
    public abstract Property<Boolean> getAcceptYandexMystemLicense();

    @Input
    public abstract Property<String> getArchiveUrl();

    @Input
    @Optional
    public abstract Property<String> getExpectedSha256();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @OutputFile
    public abstract RegularFileProperty getMetadataFile();

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
        String expectedSha256 = getExpectedSha256().getOrNull();
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
            downloadTo(URI.create(getArchiveUrl().get()), temporaryArchive);
            if (!matchesExpectedSha256(temporaryArchive, expectedSha256)) {
                Files.deleteIfExists(temporaryArchive);
                throw new GradleException("Downloaded MyStem archive checksum does not match expected sha256.");
            }
            moveReplacing(temporaryArchive, archive);
            writeMetadata(metadata, getVersion().get(), getArchiveUrl().get(), expectedSha256);
            getLogger().lifecycle("Downloaded MyStem {} to {}", getVersion().get(), archive);
        } catch (IOException error) {
            throw new GradleException("Failed to download MyStem archive from " + getArchiveUrl().get(), error);
        }
    }

    private static void downloadTo(URI uri, Path destination) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
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
        return expectedSha256.trim().toLowerCase();
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
