import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    kotlin("jvm")
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
}

mystem4jJava {
    automaticModuleName.set("io.github.ulviar.mystem4j.kotlin")
}

mystem4jPublishing {
    moduleDescription.set("Kotlin DSL and extension helpers for MyStem4j runtime APIs.")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":mystem4j-runtime"))

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Javadoc>("javadoc") {
    source = fileTree("src/main/java") {
        include("__no_javadoc_sources__")
    }
}

tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
    from(layout.buildDirectory.dir("dokka/javadoc"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
