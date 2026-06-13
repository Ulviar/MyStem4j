plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-gradle-plugin`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

mystem4jJava {
    automaticModuleName.set("io.github.ulviar.mystem4j.gradle.plugin")
}

mystem4jPublishing {
    moduleDescription.set("Gradle plugin for preparing the native MyStem binary.")
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
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
