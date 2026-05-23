package io.github.ulviar.mystem4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class MystemDistributionTest {
    @Test
    void resolvesLinuxDistribution() {
        MystemDistribution distribution = MystemDistribution.forOs("linux", "3.1");

        assertEquals("mystem-3.1-linux-64bit.tar.gz", distribution.archiveName());
        assertEquals("tar.gz", distribution.archiveType());
        assertEquals("mystem", distribution.executableName());
        assertEquals("linux-x64", distribution.platformId());
        assertEquals(
                "https://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz",
                distribution.archiveUrl("https://download.cdn.yandex.net/mystem"));
    }

    @Test
    void resolvesMacosDistribution() {
        MystemDistribution distribution = MystemDistribution.forOs("Mac OS X", "3.1");

        assertEquals("mystem-3.1-macosx.tar.gz", distribution.archiveName());
        assertEquals("macos-x64", distribution.platformId());
    }

    @Test
    void resolvesWindowsDistribution() {
        MystemDistribution distribution = MystemDistribution.forOs("windows", "3.1");

        assertEquals("mystem-3.1-win-64bit.zip", distribution.archiveName());
        assertEquals("zip", distribution.archiveType());
        assertEquals("mystem.exe", distribution.executableName());
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThrows(GradleException.class, () -> MystemDistribution.forOs("linux", "3.0"));
    }
}
