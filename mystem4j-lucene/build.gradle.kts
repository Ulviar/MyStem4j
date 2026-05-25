plugins {
    `java-library`
    `maven-publish`
}

val luceneVersion = providers.gradleProperty("lucene.version").orElse("10.4.0")

dependencies {
    api(project(":mystem4j-runtime"))
    api(project(":mystem4j-tokenization"))
    api("org.apache.lucene:lucene-core:${luceneVersion.get()}")

    implementation(project(":mystem4j-model"))

    testImplementation("org.apache.lucene:lucene-test-framework:${luceneVersion.get()}")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable").orElse("")

    inputs.property("mystem4j.executable", realMystemExecutable)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
}

tasks.register<Test>("memorySmokeTest") {
    group = "verification"
    description = "Runs Lucene memory-retention smoke tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "96m"
    shouldRunAfter(tasks.named("test"))
    filter {
        includeTestsMatching(
            "io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzerTest.testTokenizerReleasesBufferedFieldDataOnClose")
        includeTestsMatching("io.github.ulviar.mystem4j.lucene.MystemLuceneMemorySmokeTest.*")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
