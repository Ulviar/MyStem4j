package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaConventionsArtifactsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void allJarVariantsSupportAbsentOrConfiguredModuleNamesAndConfigurationCache() throws IOException {
        for (boolean configured : List.of(false, true)) {
            Path project = temporaryDirectory.resolve(configured ? "named" : "unnamed");
            Files.createDirectories(project.resolve("src/main/java/example"));
            Files.writeString(project.resolve("settings.gradle"), """
                    rootProject.name = 'artifact-fixture'
                    dependencyResolutionManagement {
                        versionCatalogs { libs { version('jacoco', '0.8.15') } }
                    }
                    """);
            Files.writeString(project.resolve("build.gradle"), """
                    plugins {
                        id 'java'
                        id 'io.github.ulviar.mystem4j.java-conventions'
                    }
                    """ + (configured ? "mystem4jJava.automaticModuleName.set('example.fixture')\n" : ""));
            Files.writeString(project.resolve("src/main/java/example/Example.java"), """
                    package example;
                    /** A minimal consumer of the artifact conventions. */
                    public final class Example {}
                    """);
            GradleRunner runner = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                    .withArguments("jar", "sourcesJar", "javadocJar", "--configuration-cache", "--offline", "--stacktrace");
            runner.build();
            assertTrue(runner.build().getOutput().contains("Reusing configuration cache."));
            for (String suffix : List.of("", "-sources", "-javadoc")) {
                try (JarFile jar = new JarFile(project.resolve("build/libs/artifact-fixture" + suffix + ".jar").toFile())) {
                    assertEquals(configured ? "example.fixture" : null,
                            jar.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
                    assertTrue(jar.stream().anyMatch(entry -> !entry.isDirectory() && !entry.getName().startsWith("META-INF/")));
                    if (suffix.isEmpty()) {
                        try (DataInputStream bytecode = new DataInputStream(
                                jar.getInputStream(jar.getJarEntry("example/Example.class")))) {
                            assertEquals(0xCAFEBABE, bytecode.readInt());
                            assertEquals(0, bytecode.readUnsignedShort(), "Published bytecode must not use preview features.");
                            assertEquals(69, bytecode.readUnsignedShort(), "Published bytecode must target Java 25.");
                        }
                    }
                }
            }
        }
    }
}
