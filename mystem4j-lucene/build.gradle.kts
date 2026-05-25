plugins {
    `java-library`
    `maven-publish`
}

val luceneVersion = providers.gradleProperty("lucene.version").orElse("9.12.3")

dependencies {
    api(project(":mystem4j-runtime"))
    api(project(":mystem4j-tokenization"))
    api("org.apache.lucene:lucene-core:${luceneVersion.get()}")

    implementation(project(":mystem4j-model"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
