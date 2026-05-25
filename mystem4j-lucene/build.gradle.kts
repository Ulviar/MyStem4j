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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
