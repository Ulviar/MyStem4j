import java.nio.file.Path
import io.github.ulviar.mystem4j.build.ApiSurfaceCheckTask
import io.github.ulviar.mystem4j.build.JpmsSmokeTestTask
import io.github.ulviar.mystem4j.build.PublicationMetadataCheckTask
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    base
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
}

val mystem4jVersion = providers.gradleProperty("mystem4j.version").orElse("0.1.0")
val projectUrlValue = "https://github.com/Ulviar/MyStem4j"
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
val libraryProjectNames = listOf(
    "mystem4j-runtime",
    "mystem4j-model",
    "mystem4j-tokenization",
    "mystem4j-lucene",
    "mystem4j-kotlin"
)
val apiSurfaceProjectNames = libraryProjectNames + "mystem4j-gradle-plugin"
val javaBinSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
val javaHome = Path.of(System.getProperty("java.home"))
val javaExePath = javaHome.resolve("bin/java$javaBinSuffix").toAbsolutePath().toString()
val javacExePath = javaHome.resolve("bin/javac$javaBinSuffix").toAbsolutePath().toString()
val javapExePath = javaHome.resolve("bin/javap$javaBinSuffix").toAbsolutePath().toString()

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
                    url.set(projectUrlValue)
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
                        connection.set("scm:git:$projectUrlValue.git")
                        developerConnection.set("scm:git:$projectUrlValue.git")
                        url.set(projectUrlValue)
                    }
                }
            }
            repositories {
                maven {
                    name = "releaseDryRun"
                    url = rootProject.layout.buildDirectory.dir("release-dry-run-repo").get().asFile.toURI()
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

tasks.register("realMystemUnicodeStress") {
    group = "verification"
    description = "Runs the exhaustive real MyStem Unicode offset stress test."
    dependsOn(":mystem4j-model:realMystemUnicodeStress")
}

tasks.register("memorySmokeTest") {
    group = "verification"
    description = "Runs lightweight memory-retention smoke tests."
    dependsOn(
        ":mystem4j-model:memorySmokeTest",
        ":mystem4j-runtime:memorySmokeTest",
        ":mystem4j-tokenization:memorySmokeTest",
        ":mystem4j-lucene:memorySmokeTest"
    )
}

tasks.register<JpmsSmokeTestTask>("jpmsSmokeTest") {
    group = "verification"
    description = "Compiles and runs a small modular consumer against the published library modules."
    dependsOn(libraryProjectNames.map { ":$it:jar" })
    getWorkDirectory().set(layout.buildDirectory.dir("jpms-smoke"))
    getJavaExecutable().set(javaExePath)
    getJavacExecutable().set(javacExePath)
    for (projectName in libraryProjectNames) {
        val moduleProject = project(":$projectName")
        getModulePath().from(moduleProject.tasks.named<Jar>("jar").flatMap { it.archiveFile })
        moduleProject.configurations.findByName("runtimeClasspath")?.let { getModulePath().from(it) }
    }
}

tasks.register<PublicationMetadataCheckTask>("publicationMetadataCheck") {
    group = "verification"
    description = "Checks generated JAR module descriptors and Maven publication metadata."
    dependsOn(libraryProjectNames.map { ":$it:jar" })
    dependsOn(libraryProjectNames.map { ":$it:generatePomFileForMavenJavaPublication" })
    dependsOn(":mystem4j-gradle-plugin:generatePomFileForPluginMavenPublication")
    dependsOn(":mystem4j-gradle-plugin:generatePomFileForMystem4jPluginMarkerMavenPublication")
    getProjectUrl().set(projectUrlValue)
    for (projectName in libraryProjectNames) {
        val moduleProject = project(":$projectName")
        getModuleNameByProject().put(projectName, automaticModuleNames.getValue(projectName))
        getJarPathByProject().put(
            projectName,
            moduleProject.tasks.named<Jar>("jar").flatMap { it.archiveFile }.map { it.asFile.absolutePath }
        )
        getPomPathByProject().put(
            projectName,
            moduleProject.layout.buildDirectory.file("publications/mavenJava/pom-default.xml")
                .map { it.asFile.absolutePath }
        )
    }
    getPluginPomPath().set(project(":mystem4j-gradle-plugin")
        .layout
        .buildDirectory
        .file("publications/pluginMaven/pom-default.xml")
        .map { it.asFile.absolutePath })
    getCompileDependenciesByProject().put("mystem4j-runtime", "icli")
    getCompileDependenciesByProject().put("mystem4j-model", "jackson-core")
    getCompileDependenciesByProject().put("mystem4j-tokenization", "mystem4j-model")
    getCompileDependenciesByProject().put("mystem4j-lucene", "mystem4j-runtime,mystem4j-tokenization,lucene-core")
    getCompileDependenciesByProject().put("mystem4j-kotlin", "mystem4j-runtime,kotlin-stdlib")
}

tasks.register<ApiSurfaceCheckTask>("apiSurfaceCheck") {
    group = "verification"
    description = "Checks the public API surface against the committed javap baseline."
    dependsOn(apiSurfaceProjectNames.map { ":$it:jar" })
    getBaselineDirectory().set(layout.projectDirectory.dir("config/api-baseline"))
    getReportDirectory().set(layout.buildDirectory.dir("reports/api-surface"))
    getUpdateBaseline().set(providers.gradleProperty("mystem4j.updateApiBaseline").map(String::toBoolean).orElse(false))
    getJavapExecutable().set(javapExePath)
    for (projectName in apiSurfaceProjectNames) {
        getJarPathByProject().put(
            projectName,
            project(":$projectName").tasks.named<Jar>("jar").flatMap { it.archiveFile }.map { it.asFile.absolutePath }
        )
    }
}

tasks.named("check") {
    dependsOn("jpmsSmokeTest", "publicationMetadataCheck", "apiSurfaceCheck")
}

tasks.register("publishToReleaseDryRunRepository") {
    group = "publishing"
    description = "Publishes all release artifacts to build/release-dry-run-repo."
    dependsOn(apiSurfaceProjectNames.map { ":$it:publishAllPublicationsToReleaseDryRunRepository" })
}

tasks.register("releaseCandidateCheck") {
    group = "verification"
    description = "Runs local release gates that do not require a real MyStem executable."
    dependsOn(
        "check",
        "memorySmokeTest",
        "publishToReleaseDryRunRepository",
        ":mystem4j-benchmarks:jmhSmoke"
    )
}
