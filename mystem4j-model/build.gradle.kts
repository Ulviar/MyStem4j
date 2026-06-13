plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

mystem4jJava {
    automaticModuleName.set("io.github.ulviar.mystem4j.model")
}

mystem4jPublishing {
    moduleDescription.set("MyStem JSON model parsing, grammar parsing, Unicode preparation, and offset alignment.")
}

val realMystemTest by sourceSets.creating {
    java.srcDir("src/realMystemTest/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

dependencies {
    api(libs.jackson.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jazzer.junit)
    testRuntimeOnly(libs.junit.platform.launcher)

    add(realMystemTest.implementationConfigurationName, project(":mystem4j-runtime"))
}

configurations[realMystemTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[realMystemTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")
    val unicodeStress = providers.systemProperty("mystem4j.unicodeStress").orElse("false")
    val unicodeStressChunkSize = providers.systemProperty("mystem4j.unicodeStressChunkSize").orElse("2048")

    // Jazzer instruments fuzz tests; disabling CDS keeps JDK output free from bootstrap-classpath warnings.
    jvmArgs("-Xshare:off")
    inputs.property("mystem4j.executable", realMystemExecutable)
    inputs.property("mystem4j.unicodeStress", unicodeStress)
    inputs.property("mystem4j.unicodeStressChunkSize", unicodeStressChunkSize)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
    systemProperty("mystem4j.unicodeStress", unicodeStress.get())
    systemProperty("mystem4j.unicodeStressChunkSize", unicodeStressChunkSize.get())
}

tasks.register<Test>("realMystemUnicodeStress") {
    group = "verification"
    description = "Runs the exhaustive real MyStem Unicode offset stress test."

    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")
    val unicodeStressChunkSize = providers.systemProperty("mystem4j.unicodeStressChunkSize").orElse("2048")
    val unicodeStressTestName =
        "io.github.ulviar.mystem4j.model.RealMystemUnicodeOffsetIntegrationTest" +
            ".stressChecksAllUnicodeScalarValuesWithRealMystem"

    testClassesDirs = realMystemTest.output.classesDirs
    classpath = realMystemTest.runtimeClasspath
    inputs.property("mystem4j.executable", realMystemExecutable)
    inputs.property("mystem4j.unicodeStressChunkSize", unicodeStressChunkSize)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
    systemProperty("mystem4j.unicodeStress", "true")
    systemProperty("mystem4j.unicodeStressChunkSize", unicodeStressChunkSize.get())
    shouldRunAfter(tasks.test)
    filter {
        includeTestsMatching(unicodeStressTestName)
    }
    doFirst {
        if (realMystemExecutable.get().isBlank()) {
            throw GradleException(
                "Set -Dmystem4j.executable=/path/to/mystem to run the real MyStem Unicode stress test."
            )
        }
    }
}

tasks.register<Test>("realMystemTest") {
    group = "verification"
    description = "Runs model integration tests against a real MyStem executable."

    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")

    testClassesDirs = realMystemTest.output.classesDirs
    classpath = realMystemTest.runtimeClasspath
    inputs.property("mystem4j.executable", realMystemExecutable)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
    shouldRunAfter(tasks.named("test"))
    doFirst {
        if (realMystemExecutable.get().isBlank()) {
            throw GradleException("Set -Dmystem4j.executable=/path/to/mystem to run real MyStem tests.")
        }
    }
}

tasks.register<Test>("memorySmokeTest") {
    group = "verification"
    description = "Runs model memory-retention smoke tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "96m"
    shouldRunAfter(tasks.named("test"))
    filter {
        includeTestsMatching("io.github.ulviar.mystem4j.model.MystemModelMemorySmokeTest.*")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
