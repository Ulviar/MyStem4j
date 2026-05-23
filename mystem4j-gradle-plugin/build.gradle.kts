plugins {
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins {
        create("mystem4j") {
            id = "io.github.ulviar.mystem4j"
            implementationClass = "io.github.ulviar.mystem4j.gradle.Mystem4jPlugin"
            displayName = "MyStem4j Gradle Plugin"
            description = "Downloads, caches, extracts, and probes the MyStem binary for MyStem4j builds."
        }
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")
}
