package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishingConventionsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void ordinaryLocalPublicationNeedsNoSigningOrCentralCredentials() throws IOException {
        Path project = fixture("ordinary");
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(":library:publishAllPublicationsToReleaseDryRunRepository",
                        ":plugin:publishAllPublicationsToReleaseDryRunRepository", "--stacktrace")
                .build();
        Path repository = project.resolve("build/release-dry-run-repo");
        assertTrue(Files.exists(repository.resolve("io/github/example/library/1.0/library-1.0.jar")));
        assertTrue(Files.exists(repository.resolve("io/github/example/plugin/1.0/plugin-1.0.jar")));
        assertTrue(Files.exists(repository.resolve(
                "io/github/example/fixture/io.github.example.fixture.gradle.plugin/1.0/"
                        + "io.github.example.fixture.gradle.plugin-1.0.pom")));
        try (var files = Files.walk(repository)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".asc")));
        }
    }

    @Test
    void centralOptInPreservesPublicationsAndUsesGpgWithoutAutomaticRelease() throws IOException {
        Path project = fixture("central");
        // Only configure the publishing tasks: no signing process or remote upload runs in this test.
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("help", "-Pmystem4j.centralPublishing=true",
                        "-PmavenCentralAutomaticPublishing=true", "--stacktrace")
                .build();
        assertFalse(Files.exists(project.resolve("build/publishing/mavenCentral")));
    }

    @Test
    void kotlinSourceArchiveRemainsCompleteAcrossTaskOrderAndConfigurationCache() throws IOException {
        Path project = fixture("kotlin-sources");
        String[] arguments = {":kotlinLibrary:sourcesJar", ":kotlinLibrary:kotlinSourcesJar",
                ":kotlinLibrary:generateMetadataFileForMavenJavaPublication",
                "-Pmystem4j.centralPublishing=true", "--rerun-tasks", "--configuration-cache", "--stacktrace"};
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(arguments).build();
        Path archive = project.resolve("kotlinLibrary/build/libs/kotlinLibrary-1.0-sources.jar");
        byte[] first = Files.readAllBytes(archive);
        String metadata = Files.readString(project.resolve("kotlinLibrary/build/publications/mavenJava/module.json"));
        assertEquals(1, metadata.lines()
                .filter(line -> line.contains("\"url\": \"kotlinLibrary-1.0-sources.jar\"")).count());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Set<String> entries = zip.stream().filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName()).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("META-INF/MANIFEST.MF", "example/JavaExample.java", "example/KotlinExample.kt"),
                    entries);
        }
        var cached = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(arguments).build();
        assertTrue(cached.getOutput().contains("Reusing configuration cache."));
        assertArrayEquals(first, Files.readAllBytes(archive));
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(":kotlinLibrary:kotlinSourcesJar", ":kotlinLibrary:sourcesJar",
                        "-Pmystem4j.centralPublishing=true",
                        "--rerun-tasks", "--configuration-cache", "--stacktrace").build();
        assertArrayEquals(first, Files.readAllBytes(archive));
    }

    private Path fixture(String name) throws IOException {
        Path project = temporaryDirectory.resolve(name);
        Files.createDirectories(project);
        Files.writeString(project.resolve("settings.gradle"), """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                rootProject.name = 'publishing-fixture'
                include 'library', 'kotlinLibrary', 'plugin', 'benchmark'
                """);
        Files.writeString(project.resolve("build.gradle"), """
                import com.vanniktech.maven.publish.MavenPublishBaseExtension
                import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
                import org.gradle.plugins.signing.Sign
                import org.gradle.plugins.signing.SigningExtension

                plugins {
                    id 'io.github.ulviar.mystem4j.publishing-conventions' apply false
                    id 'com.vanniktech.maven.publish.base' version '%s' apply false
                    id 'org.jetbrains.kotlin.jvm' version '%s' apply false
                }
                allprojects {
                    group = 'io.github.example'
                    version = '1.0'
                }
                if (providers.gradleProperty('mystem4j.centralPublishing')
                        .map(Boolean::parseBoolean).getOrElse(false)) {
                    subprojects { child ->
                        child.plugins.withId('io.github.ulviar.mystem4j.publishing-conventions') {
                            child.pluginManager.apply('com.vanniktech.maven.publish.base')
                            child.extensions.configure(MavenPublishBaseExtension) { central ->
                                central.publishToMavenCentral(false)
                                central.signAllPublications()
                            }
                            child.extensions.configure(SigningExtension) { signing -> signing.useGpgCmd() }
                        }
                    }
                }
                project(':library') {
                    apply plugin: 'java-library'
                    apply plugin: 'maven-publish'
                    apply plugin: 'io.github.ulviar.mystem4j.publishing-conventions'
                    java { withSourcesJar(); withJavadocJar() }
                    publishing {
                        publications { mavenJava(MavenPublication) { from components.java } }
                    }
                }
                project(':plugin') {
                    apply plugin: 'java-gradle-plugin'
                    apply plugin: 'maven-publish'
                    apply plugin: 'io.github.ulviar.mystem4j.publishing-conventions'
                    java { withSourcesJar(); withJavadocJar() }
                    gradlePlugin {
                        plugins {
                            fixturePlugin {
                                id = 'io.github.example.fixture'
                                implementationClass = 'example.FixturePlugin'
                            }
                        }
                    }
                }
                project(':kotlinLibrary') {
                    apply plugin: 'org.jetbrains.kotlin.jvm'
                    apply plugin: 'java-library'
                    apply plugin: 'maven-publish'
                    apply plugin: 'io.github.ulviar.mystem4j.publishing-conventions'
                    repositories { mavenCentral() }
                    java { withSourcesJar(); withJavadocJar() }
                    // Match the module: the Java source archive includes both source languages.
                    tasks.named('kotlinSourcesJar') { enabled = false }
                    tasks.withType(Jar).configureEach {
                        preserveFileTimestamps = false
                        reproducibleFileOrder = true
                    }
                    publishing {
                        publications { mavenJava(MavenPublication) { from components.java } }
                    }
                }
                project(':benchmark') { apply plugin: 'java' }
                gradle.projectsEvaluated {
                    def central = providers.gradleProperty('mystem4j.centralPublishing')
                            .map(Boolean::parseBoolean).getOrElse(false)
                    [project(':library'), project(':kotlinLibrary'), project(':plugin')].each { published ->
                        def expected = published.name == 'plugin'
                                ? ['pluginMaven', 'fixturePluginPluginMarkerMaven'] : ['mavenJava']
                        assert published.publishing.publications.names == expected.toSet()
                        assert published.publishing.repositories.names ==
                                (central ? ['releaseDryRun', 'mavenCentral'] : ['releaseDryRun']).toSet()
                        assert published.plugins.hasPlugin('com.vanniktech.maven.publish.base') == central
                        assert published.plugins.hasPlugin('signing') == central
                        assert published.tasks.names.contains('publishToMavenCentral') == central
                        if (central) {
                            assert published.signing.signatories.class.simpleName == 'GnupgSignatoryProvider'
                            assert published.tasks.withType(Sign).names ==
                                    expected.collect { 'sign' + it.capitalize() + 'Publication' }.toSet()
                            def publishTaskNames = published.tasks.withType(PublishToMavenRepository).names.toList()
                            assert publishTaskNames.size() == expected.size() * 2
                            publishTaskNames.each { taskName ->
                                def task = published.tasks.named(taskName, PublishToMavenRepository).get()
                                def dependencies = task.taskDependencies.getDependencies(task)
                                assert dependencies.every { dependency ->
                                    !dependency.class.name.contains('EnableAutomaticMavenCentralPublishingTask')
                                }
                                if (task.repository.name == 'releaseDryRun') {
                                    assert dependencies.every { !it.name.contains('MavenCentral') }
                                }
                            }
                        }
                    }
                    def benchmark = project(':benchmark')
                    assert !benchmark.plugins.hasPlugin('maven-publish')
                    assert !benchmark.plugins.hasPlugin('com.vanniktech.maven.publish.base')
                    assert !benchmark.plugins.hasPlugin('signing')
                    assert !benchmark.tasks.names.contains('publishToMavenCentral')
                }
                """.formatted(System.getProperty("mystem4j.test.centralPluginVersion"),
                        System.getProperty("mystem4j.test.kotlinVersion")));
        writeSource(project, "library", "Example", """
                package example;
                /** An ordinary published library. */
                public final class Example {}
                """);
        writeSource(project, "plugin", "FixturePlugin", """
                package example;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                /** A plugin whose marker publication must be preserved. */
                public final class FixturePlugin implements Plugin<Project> {
                    @Override public void apply(Project project) {}
                }
                """);
        Files.createDirectories(project.resolve("benchmark"));
        writeSource(project, "kotlinLibrary", "JavaExample", """
                package example;
                public final class JavaExample {}
                """);
        Path kotlinSource = project.resolve("kotlinLibrary/src/main/kotlin/example/KotlinExample.kt");
        Files.createDirectories(kotlinSource.getParent());
        Files.writeString(kotlinSource, "package example\nclass KotlinExample\n");
        return project;
    }

    private static void writeSource(Path project, String module, String name, String contents) throws IOException {
        Path source = project.resolve(module + "/src/main/java/example/" + name + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, contents);
    }
}
