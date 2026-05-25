import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
}

val mystem4jVersion = providers.gradleProperty("mystem4j.version").orElse("0.1.0-SNAPSHOT")
val projectUrl = "https://github.com/Ulviar/MyStem4j"
val moduleDescriptions = mapOf(
    "mystem4j-runtime" to "MyStem CLI runtime for JVM applications.",
    "mystem4j-model" to "MyStem JSON model parsing, grammar parsing, Unicode preparation, and offset alignment.",
    "mystem4j-tokenization" to "Search-oriented token preparation above parsed MyStem output.",
    "mystem4j-lucene" to "Apache Lucene Analyzer and Tokenizer integration for MyStem.",
    "mystem4j-kotlin" to "Kotlin DSL and extension helpers for MyStem4j runtime APIs.",
    "mystem4j-gradle-plugin" to "Gradle plugin for preparing the native MyStem binary."
)
val automaticModuleNames = mapOf(
    "mystem4j-runtime" to "io.github.ulviar.mystem4j",
    "mystem4j-model" to "io.github.ulviar.mystem4j.model",
    "mystem4j-tokenization" to "io.github.ulviar.mystem4j.tokenization",
    "mystem4j-lucene" to "io.github.ulviar.mystem4j.lucene",
    "mystem4j-kotlin" to "io.github.ulviar.mystem4j.kotlin",
    "mystem4j-gradle-plugin" to "io.github.ulviar.mystem4j.gradle.plugin"
)
val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")

allprojects {
    group = "io.github.ulviar.mystem4j"
    version = mystem4jVersion.get()
}

subprojects {
    plugins.withType<JavaPlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
            options.encoding = "UTF-8"
        }

        tasks.withType<Jar>().configureEach {
            automaticModuleNames[project.name]?.let { moduleName ->
                manifest {
                    attributes["Automatic-Module-Name"] = moduleName
                }
            }
        }

        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set(moduleDescriptions[project.name] ?: "MyStem4j module.")
                    url.set(projectUrl)
                    developers {
                        developer {
                            id.set("ulviar")
                            name.set("Ulviar")
                        }
                    }
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        connection.set("scm:git:$projectUrl.git")
                        developerConnection.set("scm:git:$projectUrl.git")
                        url.set(projectUrl)
                    }
                }
            }
        }
    }
}

tasks.register("realMystemTest") {
    group = "verification"
    description = "Runs test suites with real MyStem integration tests enabled."
    inputs.property("mystem4j.executable", realMystemExecutable)
    dependsOn(
        ":mystem4j-runtime:test",
        ":mystem4j-model:test",
        ":mystem4j-tokenization:test",
        ":mystem4j-lucene:test"
    )
    doFirst {
        if (realMystemExecutable.get().isBlank()) {
            throw GradleException("Set -Dmystem4j.executable=/path/to/mystem to run real MyStem integration tests.")
        }
    }
}
