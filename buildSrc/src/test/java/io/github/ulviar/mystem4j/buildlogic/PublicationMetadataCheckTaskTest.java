package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class PublicationMetadataCheckTaskTest {
    @Test
    void pluginCannotAcquireAnUnusedRuntimeDependency() {
        String pom = """
                <project><dependencies>
                  <dependency><artifactId>mystem4j-runtime</artifactId><scope>runtime</scope></dependency>
                </dependencies></project>
                """;
        assertThrows(GradleException.class, () -> PublicationMetadataCheckTask.requireDependencyArtifacts(
                "gradle-plugin", pom, java.util.Set.of()));
        assertDoesNotThrow(() -> PublicationMetadataCheckTask.requireDependencyArtifacts(
                "gradle-plugin", "<project/>", java.util.Set.of()));
    }

    @Test
    void scopeMustBelongToTheRequestedDependency() {
        String pom = """
                <project><dependencies>
                  <dependency><artifactId>procwright</artifactId><scope>compile</scope></dependency>
                  <dependency><artifactId>another-library</artifactId><scope>runtime</scope></dependency>
                </dependencies></project>
                """;
        assertThrows(GradleException.class,
                () -> PublicationMetadataCheckTask.requireDependencyScope("runtime", pom, "procwright", "runtime"));
        assertDoesNotThrow(
                () -> PublicationMetadataCheckTask.requireDependencyScope("runtime", pom, "procwright", "compile"));
    }

    @Test
    void dependencyManagementDoesNotSupplyAnActualDependency() {
        String pom = """
                <project><dependencyManagement><dependencies>
                  <dependency><artifactId>procwright</artifactId><scope>runtime</scope></dependency>
                </dependencies></dependencyManagement></project>
                """;
        assertThrows(GradleException.class,
                () -> PublicationMetadataCheckTask.requireDependencyScope("runtime", pom, "procwright", "runtime"));
    }

    @Test
    void defaultScopeIsCompile() {
        String pom = "<project><dependencies><dependency><artifactId>model</artifactId></dependency></dependencies></project>";
        assertDoesNotThrow(
                () -> PublicationMetadataCheckTask.requireDependencyScope("tokenization", pom, "model", "compile"));
    }
}
