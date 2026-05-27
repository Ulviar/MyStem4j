plugins {
    `java-library`
    `maven-publish`
}

val realMystemTest by sourceSets.creating {
    java.srcDir("src/realMystemTest/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

dependencies {
    api(project(":mystem4j-model"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")

    add(realMystemTest.implementationConfigurationName, project(":mystem4j-runtime"))
}

configurations[realMystemTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[realMystemTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")

    inputs.property("mystem4j.executable", realMystemExecutable)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
}

tasks.register<Test>("memorySmokeTest") {
    group = "verification"
    description = "Runs tokenization memory-retention smoke tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "96m"
    shouldRunAfter(tasks.named("test"))
    filter {
        includeTestsMatching("io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerMemorySmokeTest.*")
    }
}

tasks.register<Test>("realMystemTest") {
    group = "verification"
    description = "Runs tokenization integration tests against a real MyStem executable."

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
