plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api("com.github.ulviar:icli:0.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
