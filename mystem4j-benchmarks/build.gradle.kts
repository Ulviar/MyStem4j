plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    java
}

dependencies {
    implementation(project(":mystem4j-model"))
    implementation(project(":mystem4j-tokenization"))
    implementation(project(":mystem4j-lucene"))
    implementation(libs.jmh.core)

    annotationProcessor(libs.jmh.generator.annprocess)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Runs MyStem4j JMH benchmarks. Pass -PjmhArgs='...' to customize JMH arguments."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    val configuredArgs = providers.gradleProperty("jmhArgs").orElse(".*MystemCoreBenchmark.*")
    val corpus = providers.gradleProperty("benchmarkCorpusDir").orElse("")
    doFirst {
        setArgs(configuredArgs.get().split(Regex("\\s+")).filter(String::isNotBlank) + listOf("-p", "corpusDirectory=${corpus.get()}"))
    }
}

tasks.register<JavaExec>("jmhSmoke") {
    group = "verification"
    description = "Runs a short JMH smoke benchmark to verify benchmark wiring."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(".*MystemCoreBenchmark.*", "-p", "inputChars=1024", "-p", "documentIndex=0", "-wi", "1", "-i", "1", "-f", "1", "-w", "100ms", "-r", "100ms", "-foe", "true")
}

tasks.register<JavaExec>("nativeJmh") {
    group = "benchmark"
    description = "Measures real MyStem requests, Lucene analysis and indexing; requires -Dmystem4j.executable."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    val executable = providers.systemProperty("mystem4j.executable")
    val configuredArgs = providers.gradleProperty("nativeJmhArgs").orElse(".*MystemNativeBenchmark.*")
    val report = layout.buildDirectory.file("reports/jmh/native.json")
    val corpus = providers.gradleProperty("benchmarkCorpusDir").orElse("")
    doFirst {
        check(executable.isPresent && executable.get().isNotBlank()) {
            "nativeJmh requires -Dmystem4j.executable=/path/to/mystem"
        }
        report.get().asFile.parentFile.mkdirs()
        setArgs(configuredArgs.get().split(Regex("\\s+")).filter(String::isNotBlank) + listOf(
            "-p", "executable=${executable.get()}", "-rf", "json", "-rff", report.get().asFile.absolutePath,
            "-p", "corpusDirectory=${corpus.get()}",
            "-foe", "true", "-prof", "gc"
        ))
    }
}

tasks.register("jmhCompileCheck") {
    group = "verification"
    description = "Compiles JMH benchmarks without running them."
    dependsOn(tasks.named("classes"))
}

tasks.named("check") {
    dependsOn(tasks.named("jmhCompileCheck"))
}
