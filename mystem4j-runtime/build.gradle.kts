plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

val realMystemPoolSoakTest by sourceSets.creating {
    java.srcDir("src/realMystemPoolSoakTest/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

mystem4jJava {
    automaticModuleName.set("io.github.ulviar.mystem4j")
}

mystem4jPublishing {
    moduleDescription.set("MyStem CLI runtime for JVM applications.")
}

dependencies {
    api("com.github.ulviar:icli:0.1.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

configurations[realMystemPoolSoakTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[realMystemPoolSoakTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable")
        .map { rootProject.file(it).absolutePath }
        .orElse("")
    inputs.property("mystem4j.executable", realMystemExecutable)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
}

tasks.register<Test>("memorySmokeTest") {
    group = "verification"
    description = "Runs runtime process/resource release smoke tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    filter {
        includeTestsMatching("io.github.ulviar.mystem4j.MystemRuntimeResourceReleaseTest.*")
    }
}

tasks.register<Test>("realMystemPoolSoak") {
    group = "verification"
    description = "Runs the sustained real-MyStem pool concurrency and worker-rotation test."

    val realMystemExecutable = providers.systemProperty("mystem4j.executable")
        .map { rootProject.file(it).absolutePath }
        .orElse("")
    val requestsPerConcurrency = providers.systemProperty("mystem4j.poolSoakRequests").orElse("10000")
    val poolSize = providers.systemProperty("mystem4j.poolSoakPoolSize").orElse("4")
    val maxRequestsPerWorker = providers.systemProperty("mystem4j.poolSoakMaxRequestsPerWorker").orElse("250")

    testClassesDirs = realMystemPoolSoakTest.output.classesDirs
    classpath = realMystemPoolSoakTest.runtimeClasspath
    inputs.property("mystem4j.executable", realMystemExecutable)
    inputs.property("mystem4j.poolSoakRequests", requestsPerConcurrency)
    inputs.property("mystem4j.poolSoakPoolSize", poolSize)
    inputs.property("mystem4j.poolSoakMaxRequestsPerWorker", maxRequestsPerWorker)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
    systemProperty("mystem4j.poolSoakRequests", requestsPerConcurrency.get())
    systemProperty("mystem4j.poolSoakPoolSize", poolSize.get())
    systemProperty("mystem4j.poolSoakMaxRequestsPerWorker", maxRequestsPerWorker.get())
    maxParallelForks = 1
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
    testLogging.showStandardStreams = true
    doFirst {
        if (realMystemExecutable.get().isBlank()) {
            throw GradleException(
                "Set -Dmystem4j.executable=/path/to/mystem to run the real MyStem pool soak test."
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
