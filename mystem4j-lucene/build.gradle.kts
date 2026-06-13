plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

mystem4jJava {
    automaticModuleName.set("io.github.ulviar.mystem4j.lucene")
}

mystem4jPublishing {
    moduleDescription.set("Apache Lucene Analyzer and Tokenizer integration for MyStem.")
}

dependencies {
    api(project(":mystem4j-runtime"))
    api(project(":mystem4j-tokenization"))
    api(libs.lucene.core)

    implementation(project(":mystem4j-model"))

    testImplementation(libs.lucene.test.framework)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
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
