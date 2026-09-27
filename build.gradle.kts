import java.nio.file.Path
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.github.ulviar.mystem4j.buildlogic.ApiSurfaceCheckTask
import io.github.ulviar.mystem4j.buildlogic.AgentInfrastructureCheckTask
import io.github.ulviar.mystem4j.buildlogic.DocumentationArchiveCheckTask
import io.github.ulviar.mystem4j.buildlogic.JpmsSmokeTestTask
import io.github.ulviar.mystem4j.buildlogic.MarkdownLocalLinksCheckTask
import io.github.ulviar.mystem4j.buildlogic.PublicationMetadataCheckTask
import io.github.ulviar.mystem4j.buildlogic.ModuleBoundaryCheckTask
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import java.math.BigDecimal
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm
import org.gradle.plugins.signing.SigningExtension

plugins {
    base
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish.base) apply false
    alias(libs.plugins.spotless)
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion.set(JavaLanguageVersion.of(25))
    toolchainDownloadUrls.empty()
}

tasks.wrapper {
    networkTimeout.set(60_000)
    retries.set(3)
    retryBackOffMs.set(500)
}

val mystem4jVersion = providers.gradleProperty("mystem4j.version").orElse("0.1.0")
val projectUrlValue = "https://github.com/Ulviar/MyStem4j"
val automaticModuleNames = mapOf(
    "mystem4j-runtime" to "io.github.ulviar.mystem4j",
    "mystem4j-model" to "io.github.ulviar.mystem4j.model",
    "mystem4j-tokenization" to "io.github.ulviar.mystem4j.tokenization",
    "mystem4j-lucene" to "io.github.ulviar.mystem4j.lucene",
    "mystem4j-kotlin" to "io.github.ulviar.mystem4j.kotlin",
    "mystem4j-http-client" to "io.github.ulviar.mystem4j.http",
    "mystem4j-http-server" to "io.github.ulviar.mystem4j.server",
    "mystem4j-gradle-plugin" to "io.github.ulviar.mystem4j.gradle.plugin"
)
val libraryProjectNames = listOf(
    "mystem4j-runtime",
    "mystem4j-model",
    "mystem4j-tokenization",
    "mystem4j-lucene",
    "mystem4j-kotlin",
    "mystem4j-http-client",
    "mystem4j-http-server"
)
val apiSurfaceProjectNames = libraryProjectNames + "mystem4j-gradle-plugin"
val scopedAgentDirectories = libraryProjectNames + listOf("mystem4j-gradle-plugin", "mystem4j-benchmarks", "buildSrc")
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

// Keep Central and Kotlin plugins in the same root plugin classloader.
if (providers.gradleProperty("mystem4j.centralPublishing").map(String::toBoolean).orElse(false).get()) {
    subprojects {
        plugins.withId("io.github.ulviar.mystem4j.publishing-conventions") {
            pluginManager.apply("com.vanniktech.maven.publish.base")
            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(false)
                signAllPublications()
            }
            extensions.configure<SigningExtension> { useGpgCmd() }
        }
    }
}

apiValidation {
    ignoredProjects.addAll(
        listOf(
            "mystem4j-benchmarks",
            "mystem4j-gradle-plugin",
            "mystem4j-lucene",
            "mystem4j-model",
            "mystem4j-runtime",
            "mystem4j-http-client",
            "mystem4j-http-server",
            "mystem4j-tokenization"
        )
    )
}

tasks.register("realMystemTest") {
    group = "verification"
    description = "Runs test suites with real MyStem integration tests enabled."
    dependsOn(
        ":mystem4j-http-server:test",
        ":mystem4j-runtime:test",
        ":mystem4j-model:test",
        ":mystem4j-model:realMystemTest",
        ":mystem4j-tokenization:test",
        ":mystem4j-tokenization:realMystemTest",
        ":mystem4j-lucene:test"
    )
}

tasks.register("realMystemUnicodeStress") {
    group = "verification"
    description = "Runs the exhaustive real MyStem Unicode offset stress test."
    dependsOn(":mystem4j-model:realMystemUnicodeStress")
}

tasks.register("realMystemPoolSoak") {
    group = "verification"
    description = "Runs sustained real-MyStem pool load, latency, rotation, process, and descriptor checks."
    dependsOn(":mystem4j-runtime:realMystemPoolSoak")
}

tasks.register("unicodeContextStressTest") {
    group = "verification"
    description = "Runs exhaustive Java-side Unicode context tokenization invariants."
    dependsOn(":mystem4j-tokenization:unicodeContextStressTest")
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

val coverageThresholds = mapOf(
    "mystem4j-runtime" to ("0.82" to "0.65"),
    "mystem4j-http-client" to ("0.85" to "0.70"),
    "mystem4j-http-server" to ("0.80" to "0.65"),
    "mystem4j-model" to ("0.90" to "0.75"),
    "mystem4j-tokenization" to ("0.92" to "0.78"),
    "mystem4j-lucene" to ("0.90" to "0.70"),
    "mystem4j-kotlin" to ("0.58" to null),
    "mystem4j-gradle-plugin" to ("0.68" to "0.50")
)

coverageThresholds.forEach { (projectName, thresholds) ->
    project(":$projectName").tasks.withType<JacocoCoverageVerification>().configureEach {
        dependsOn(project(":$projectName").tasks.named("test"))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal(thresholds.first)
                }
                thresholds.second?.let { minimumBranchCoverage ->
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = BigDecimal(minimumBranchCoverage)
                    }
                }
            }
        }
    }
}

tasks.register("coverageVerification") {
    group = "verification"
    description = "Enforces per-module JaCoCo line and branch coverage floors."
    dependsOn(coverageThresholds.keys.map { ":$it:jacocoTestCoverageVerification" })
}

val consumerClasspaths = libraryProjectNames.associateWith { projectName ->
    listOf(Usage.JAVA_API, Usage.JAVA_RUNTIME).map { usageName ->
        configurations.create(projectName + "-" + usageName + "-consumer") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(usageName))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
            dependencies.add(project.dependencies.project(mapOf("path" to ":$projectName")))
        }
    }
}

val moduleBoundaryTasks = apiSurfaceProjectNames.map { projectName ->
    tasks.register<ModuleBoundaryCheckTask>(projectName + "ModuleBoundaryCheck") {
        group = "verification"
        description = "Checks production dependencies and public boundaries of $projectName."
        getComponent().set(projectName.removePrefix("mystem4j-"))
        getLibraryJar().set(project(":$projectName").tasks.named<Jar>("jar").flatMap { it.archiveFile })
        consumerClasspaths[projectName]?.let { paths ->
            getConsumerCompileClasspath().from(paths[0])
            getConsumerRuntimeClasspath().from(paths[1])
        }
    }
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Checks module dependencies, API isolation, and independent library consumers."
    dependsOn(moduleBoundaryTasks, "jpmsSmokeTest", "publicationMetadataCheck")
}

val consumerSmokeTasks = libraryProjectNames.map { projectName ->
    tasks.register<JpmsSmokeTestTask>(projectName + "ConsumerSmokeTest") {
        group = "verification"
        description = "Compiles and runs an independent $projectName consumer on classpath and module path."
        val component = projectName.removePrefix("mystem4j-")
        val paths = consumerClasspaths.getValue(projectName)
        getWorkDirectory().set(layout.buildDirectory.dir("module-consumers/$component"))
        getSourceFile().set(layout.projectDirectory.file("config/module-consumers/$component/Smoke.java"))
        getRequiredModule().set(automaticModuleNames.getValue(projectName))
        getJavaExecutable().set(javaExePath)
        getJavacExecutable().set(javacExePath)
        getCompileClasspath().from(paths[0])
        getModulePath().from(paths[1])
    }
}

tasks.register("jpmsSmokeTest") {
    group = "verification"
    description = "Checks each library independently through classpath and JPMS consumers."
    dependsOn(consumerSmokeTasks)
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
    getDependencyScopesByProject().put("mystem4j-runtime", "procwright:runtime")
    getDependencyScopesByProject().put("mystem4j-http-client", "mystem4j-runtime:compile,jackson-core:runtime")
    getDependencyScopesByProject().put("mystem4j-http-server", "mystem4j-runtime:compile,jackson-core:runtime,jetty-server:runtime")
    getDependencyScopesByProject().put("mystem4j-model", "jackson-core:runtime")
    getDependencyScopesByProject().put("mystem4j-tokenization", "mystem4j-model:compile")
    getDependencyScopesByProject()
        .put("mystem4j-lucene", "mystem4j-runtime:compile,mystem4j-tokenization:compile,lucene-core:compile,mystem4j-model:runtime")
    getDependencyScopesByProject().put("mystem4j-kotlin", "mystem4j-runtime:compile,kotlin-stdlib:compile")
}

tasks.register<ApiSurfaceCheckTask>("apiSurfaceCheck") {
    group = "verification"
    description = "Checks the public API surface against the committed javap baseline."
    dependsOn(apiSurfaceProjectNames.map { ":$it:jar" })
    getBaselineDirectory().set(layout.projectDirectory.dir("config/api-baseline"))
    getReportDirectory().set(layout.buildDirectory.dir("reports/api-surface"))
    getUpdateBaseline().set(providers.gradleProperty("mystem4j.updateApiBaseline").map(String::toBoolean).orElse(false))
    getBuilderOnlyConfigurationClasses().set(
        listOf(
            "io.github.ulviar.mystem4j.MystemOptions",
            "io.github.ulviar.mystem4j.MystemPoolOptions",
            "io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions",
            "io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions"
        )
    )
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
        target("buildSrc/src/**/*.java", "mystem4j-*/src/**/*.java", "config/module-consumers/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("mystem4j-*/src/**/*.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts", "mystem4j-*/build.gradle.kts", "samples/**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register<MarkdownLocalLinksCheckTask>("markdownLocalLinksCheck") {
    group = "verification"
    description = "Checks local Markdown links in README, agent guides, and docs."
    getMarkdownFiles().from(
        "README.md",
        "AGENTS.md",
        scopedAgentDirectories.map { "$it/AGENTS.md" },
        fileTree("docs") {
            include("**/*.md")
        }
    )
    getProjectDirectory().set(layout.projectDirectory)
}

tasks.register<AgentInfrastructureCheckTask>("agentInfrastructureCheck") {
    group = "verification"
    description = "Checks LLM instruction routing, active-work artifacts, and historical context boundaries."

    val scopedInstructionPaths = scopedAgentDirectories.map { "$it/AGENTS.md" }
    val agentPolicyPaths = listOf(
        "docs/internal/agent/README.md",
        "docs/internal/agent/context-map.md",
        "docs/internal/agent/artifact-policy.md",
        "docs/internal/agent/templates/active-work.md",
        "docs/internal/agent/evals.md",
        "docs/internal/decisions/README.md",
        "docs/internal/decisions/0001-agent-context-and-artifact-lifecycle.md",
        "docs/internal/agent-work/active/README.md",
        "docs/internal/history/README.md"
    )
    val historicalPaths = listOf(
        "docs/internal/history/specs/mystem-runtime-spec.md",
        "docs/internal/history/specs/mystem-model-spec.md",
        "docs/internal/history/specs/mystem-tokenization-spec.md",
        "docs/internal/history/specs/mystem-lucene-spec.md"
    )

    getExpectedInstructionPaths().set(listOf("AGENTS.md") + scopedInstructionPaths + agentPolicyPaths)
    getScopedInstructionPaths().set(scopedInstructionPaths)
    getExpectedHistoricalPaths().set(historicalPaths)
    getInstructionFiles().from((listOf("AGENTS.md") + scopedInstructionPaths + agentPolicyPaths).map(::file))
    getActiveWorkFiles().from(fileTree("docs/internal/agent-work/active") { include("*.md") })
    getHistoricalFiles().from(fileTree("docs/internal/history/specs") { include("**/*.md") })
    getInternalMarkdownFiles().from(fileTree("docs/internal") { include("**/*.md") })
    getRootMarkdownFiles().from(fileTree(layout.projectDirectory) { include("*.md") })
    getGitIgnoreFile().set(layout.projectDirectory.file(".gitignore"))
    getMaxRootInstructionLines().set(130)
    getMaxScopedInstructionLines().set(60)
    getMaxActiveWorkLines().set(160)
    getProjectDirectory().set(layout.projectDirectory)
}

tasks.register<DocumentationArchiveCheckTask>("documentationCheck") {
    group = "verification"
    description = "Builds API documentation JARs and checks their local navigation links."
    getDocumentationJars().from(subprojects.map { module ->
        module.tasks.named<Jar>("javadocJar").flatMap { it.archiveFile }
    })
}

tasks.named("check") {
    dependsOn(
        "unitTest",
        "coverageReport",
        "coverageVerification",
        "jpmsSmokeTest",
        "publicationMetadataCheck",
        "apiSurfaceCheck",
        "architectureCheck",
        "agentInfrastructureCheck",
        ":mystem4j-kotlin:apiCheck",
        "spotlessCheck",
        "documentationCheck",
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
    startParameter.projectProperties["mystem4j.version"] = mystem4jVersion.get()
    startParameter.projectProperties["mystem4j.download"] = "false"
}

tasks.register("releaseCandidateCheck") {
    group = "verification"
    description = "Runs local release gates that do not require a real MyStem executable."
    dependsOn(
        "check",
        "memorySmokeTest",
        "unicodeContextStressTest",
        "sampleSmokeTest",
        "publishToReleaseDryRunRepository",
        ":mystem4j-benchmarks:jmhSmoke"
    )
}
