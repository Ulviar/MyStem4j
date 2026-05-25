plugins {
    java
}

dependencies {
    implementation(project(":mystem4j-model"))
    implementation(project(":mystem4j-tokenization"))
    implementation(project(":mystem4j-lucene"))
    implementation("org.openjdk.jmh:jmh-core:1.37")

    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Runs MyStem4j JMH benchmarks. Pass -PjmhArgs='...' to customize JMH arguments."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    val configuredArgs = providers.gradleProperty("jmhArgs").orElse(".*Mystem.*Benchmark.*")
    doFirst {
        setArgs(configuredArgs.get().split(Regex("\\s+")).filter(String::isNotBlank))
    }
}

tasks.register<JavaExec>("jmhSmoke") {
    group = "verification"
    description = "Runs a short JMH smoke benchmark to verify benchmark wiring."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(".*Mystem.*Benchmark.*", "-wi", "1", "-i", "1", "-f", "1", "-w", "100ms", "-r", "100ms")
}

tasks.register("jmhCompileCheck") {
    group = "verification"
    description = "Compiles JMH benchmarks without running them."
    dependsOn(tasks.named("classes"))
}

tasks.named("check") {
    dependsOn(tasks.named("jmhCompileCheck"))
}
