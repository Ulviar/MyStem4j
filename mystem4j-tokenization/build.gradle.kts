plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":mystem4j-model"))

    testImplementation(project(":mystem4j-runtime"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
