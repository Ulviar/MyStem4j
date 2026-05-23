package io.github.ulviar.mystem4j.gradle;

import java.util.Locale;
import org.gradle.api.GradleException;

final class MystemDistribution {
    private static final String SUPPORTED_VERSION = "3.1";

    private final String version;
    private final String os;
    private final String archiveName;
    private final String archiveType;
    private final String executableName;

    private MystemDistribution(
            String version, String os, String archiveName, String archiveType, String executableName) {
        this.version = version;
        this.os = os;
        this.archiveName = archiveName;
        this.archiveType = archiveType;
        this.executableName = executableName;
    }

    static MystemDistribution forOs(String targetOs, String version) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new GradleException(
                    "Unsupported MyStem version " + version + ". MyStem4j currently supports only " + SUPPORTED_VERSION);
        }

        String normalizedOs = normalizeOs(targetOs);
        return switch (normalizedOs) {
            case "windows" -> new MystemDistribution(
                    version, normalizedOs, "mystem-3.1-win-64bit.zip", "zip", "mystem.exe");
            case "linux" -> new MystemDistribution(
                    version, normalizedOs, "mystem-3.1-linux-64bit.tar.gz", "tar.gz", "mystem");
            case "macos" -> new MystemDistribution(
                    version, normalizedOs, "mystem-3.1-macosx.tar.gz", "tar.gz", "mystem");
            default -> throw new GradleException(
                    "Unsupported MyStem target OS " + targetOs + ". Supported values: linux, macos, windows");
        };
    }

    static String currentOs() {
        return normalizeOs(System.getProperty("os.name", ""));
    }

    String archiveUrl(String baseUrl) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/" + archiveName;
    }

    String version() {
        return version;
    }

    String platformId() {
        return os + "-x64";
    }

    String archiveName() {
        return archiveName;
    }

    String archiveType() {
        return archiveType;
    }

    String executableName() {
        return executableName;
    }

    private static String normalizeOs(String os) {
        String normalized = os.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        if (normalized.contains("win")) {
            return "windows";
        }
        if (normalized.contains("mac") || normalized.contains("darwin") || normalized.contains("os-x")) {
            return "macos";
        }
        if (normalized.contains("linux")) {
            return "linux";
        }
        return normalized;
    }
}
