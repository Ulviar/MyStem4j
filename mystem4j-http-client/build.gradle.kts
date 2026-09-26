plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

mystem4jJava { automaticModuleName.set("io.github.ulviar.mystem4j.http") }
mystem4jPublishing { moduleDescription.set("MyStem4j HTTP client.") }

dependencies {
    api(project(":mystem4j-runtime"))
    implementation(libs.jackson.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
