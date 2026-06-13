plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
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
