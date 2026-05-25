plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-core:2.21.3")

    testImplementation(project(":mystem4j-runtime"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")
    val unicodeStress = providers.systemProperty("mystem4j.unicodeStress").orElse("false")
    val unicodeStressChunkSize = providers.systemProperty("mystem4j.unicodeStressChunkSize").orElse("2048")

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

    val testSourceSet = sourceSets.test.get()
    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")
    val unicodeStressChunkSize = providers.systemProperty("mystem4j.unicodeStressChunkSize").orElse("2048")
    val unicodeStressTestName =
        "io.github.ulviar.mystem4j.model.RealMystemUnicodeOffsetIntegrationTest" +
            ".stressChecksAllUnicodeScalarValuesWithRealMystem"

    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
