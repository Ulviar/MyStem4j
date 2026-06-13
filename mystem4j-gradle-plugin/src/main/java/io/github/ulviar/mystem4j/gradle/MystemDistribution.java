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
    private final String sha256;

    private MystemDistribution(
            String version, String os, String archiveName, String archiveType, String executableName, String sha256) {
        this.version = version;
        this.os = os;
        this.archiveName = archiveName;
        this.archiveType = archiveType;
        this.executableName = executableName;
        this.sha256 = sha256;
    }

    static MystemDistribution forOs(String targetOs, String version) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new GradleException(
                    "Unsupported MyStem version " + version + ". MyStem4j currently supports only " + SUPPORTED_VERSION);
        }

        String normalizedOs = normalizeOs(targetOs);
        return switch (normalizedOs) {
            case "windows" -> new MystemDistribution(
                    version,
                    normalizedOs,
                    "mystem-3.1-win-64bit.zip",
                    "zip",
                    "mystem.exe",
                    "03cdbe2c01661eb449e84771817096161203553fca4bca934dc17f1bc9e53bc8");
            case "linux" -> new MystemDistribution(
                    version,
                    normalizedOs,
                    "mystem-3.1-linux-64bit.tar.gz",
                    "tar.gz",
                    "mystem",
                    "4696f4ea8ce3ecda24ef5e8dfe7e4b16cfa5f1844edfcca31c34d636b73c0a62");
            case "macos" -> new MystemDistribution(
                    version,
                    normalizedOs,
                    "mystem-3.1-macosx.tar.gz",
                    "tar.gz",
                    "mystem",
                    "346e576ada01cc7c63414a9d91f6733bd418f496f073d13a4812aec3628e5693");
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

    String sha256() {
        return sha256;
    }

    private static String normalizeOs(String os) {
        String normalized = os.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        if (normalized.contains("aarch64") || normalized.contains("arm64")) {
            throw new GradleException(
                    "Unsupported MyStem target architecture in " + os + ". MyStem4j currently supports x64 MyStem distributions.");
        }
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
