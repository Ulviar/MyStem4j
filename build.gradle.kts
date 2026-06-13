import java.nio.file.Path
import io.github.ulviar.mystem4j.buildlogic.ApiSurfaceCheckTask
import io.github.ulviar.mystem4j.buildlogic.JpmsSmokeTestTask
import io.github.ulviar.mystem4j.buildlogic.MarkdownLocalLinksCheckTask
import io.github.ulviar.mystem4j.buildlogic.PublicationMetadataCheckTask

plugins {
    base
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

val mystem4jVersion = providers.gradleProperty("mystem4j.version").orElse("0.1.0")
val projectUrlValue = "https://github.com/Ulviar/MyStem4j"
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
val unitTestProjectNames = apiSurfaceProjectNames + "mystem4j-benchmarks"
val javaBinSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
val javaHome = Path.of(System.getProperty("java.home"))
val javaExePath = javaHome.resolve("bin/java$javaBinSuffix").toAbsolutePath().toString()
val javacExePath = javaHome.resolve("bin/javac$javaBinSuffix").toAbsolutePath().toString()
val javapExePath = javaHome.resolve("bin/javap$javaBinSuffix").toAbsolutePath().toString()

allprojects {
    group = "io.github.ulviar.mystem4j"
    version = mystem4jVersion.get()
}

apiValidation {
    ignoredProjects.addAll(
        listOf(
            "mystem4j-benchmarks",
            "mystem4j-gradle-plugin",
            "mystem4j-lucene",
            "mystem4j-model",
            "mystem4j-runtime",
            "mystem4j-tokenization"
        )
    )
}

tasks.register("realMystemTest") {
    group = "verification"
    description = "Runs test suites with real MyStem integration tests enabled."
    inputs.property("mystem4j.executable", realMystemExecutable)
    dependsOn(
        ":mystem4j-runtime:test",
        ":mystem4j-model:test",
        ":mystem4j-model:realMystemTest",
        ":mystem4j-tokenization:test",
        ":mystem4j-tokenization:realMystemTest",
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

tasks.register("unitTest") {
    group = "verification"
    description = "Runs unit and contract tests that do not require a real MyStem executable."
    dependsOn(unitTestProjectNames.map { ":$it:test" })
}

tasks.register("coverageReport") {
    group = "verification"
    description = "Generates JaCoCo coverage reports for published modules and the Gradle plugin."
    dependsOn(apiSurfaceProjectNames.map { ":$it:jacocoTestReport" })
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
        getJarFiles().from(moduleProject.tasks.named<Jar>("jar").flatMap { it.archiveFile })
        getPomPathByProject().put(
            projectName,
            moduleProject.layout.buildDirectory.file("publications/mavenJava/pom-default.xml")
                .map { it.asFile.absolutePath }
        )
        getPomFiles().from(moduleProject.layout.buildDirectory.file("publications/mavenJava/pom-default.xml"))
    }
    getPluginPomPath().set(project(":mystem4j-gradle-plugin")
        .layout
        .buildDirectory
        .file("publications/pluginMaven/pom-default.xml")
        .map { it.asFile.absolutePath })
    getPomFiles().from(project(":mystem4j-gradle-plugin")
        .layout
        .buildDirectory
        .file("publications/pluginMaven/pom-default.xml"))
    getDependencyScopesByProject().put("mystem4j-runtime", "icli:compile")
    getDependencyScopesByProject().put("mystem4j-model", "jackson-core:compile")
    getDependencyScopesByProject().put("mystem4j-tokenization", "mystem4j-model:compile")
    getDependencyScopesByProject()
        .put("mystem4j-lucene", "mystem4j-runtime:compile,mystem4j-tokenization:compile,lucene-core:compile")
    getDependencyScopesByProject().put("mystem4j-kotlin", "mystem4j-runtime:compile,kotlin-stdlib:compile")
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
        getJarFiles().from(project(":$projectName").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    }
}

spotless {
    format("text") {
        target("*.md", "*.properties", "config/**/*.txt", "docs/**/*.md", "gradle/**/*.toml")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("buildSrc/src/**/*.java", "mystem4j-*/src/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("mystem4j-*/src/**/*.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "mystem4j-*/build.gradle.kts", "samples/**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register<MarkdownLocalLinksCheckTask>("markdownLocalLinksCheck") {
    group = "verification"
    description = "Checks local Markdown links in README, CHANGELOG, and docs."
    getMarkdownFiles().from(
        "README.md",
        "CHANGELOG.md",
        fileTree("docs") {
            include("**/*.md")
        }
    )
    getProjectDirectory().set(layout.projectDirectory)
}

tasks.named("check") {
    dependsOn(
        "unitTest",
        "coverageReport",
        "jpmsSmokeTest",
        "publicationMetadataCheck",
        "apiSurfaceCheck",
        ":mystem4j-kotlin:apiCheck",
        "spotlessCheck",
        "markdownLocalLinksCheck")
}

tasks.register("publishToReleaseDryRunRepository") {
    group = "publishing"
    description = "Publishes all release artifacts to build/release-dry-run-repo."
    dependsOn(apiSurfaceProjectNames.map { ":$it:publishAllPublicationsToReleaseDryRunRepository" })
}

tasks.register<GradleBuild>("sampleSmokeTest") {
    group = "verification"
    description = "Runs the Gradle plugin smoke sample against this checkout."
    dependsOn("publishToReleaseDryRunRepository")
    dir = layout.projectDirectory.dir("samples/mystem-plugin-smoke").asFile
    tasks = listOf("help")
    startParameter.projectProperties["mystem4j.releaseDryRunRepository"] =
        layout.buildDirectory.dir("release-dry-run-repo").get().asFile.toURI().toString()
    startParameter.projectProperties["mystem4j.download"] = "false"
}

tasks.register("releaseCandidateCheck") {
    group = "verification"
    description = "Runs local release gates that do not require a real MyStem executable."
    dependsOn(
        "check",
        "memorySmokeTest",
        "sampleSmokeTest",
        "publishToReleaseDryRunRepository",
        ":mystem4j-benchmarks:jmhSmoke"
    )
}
