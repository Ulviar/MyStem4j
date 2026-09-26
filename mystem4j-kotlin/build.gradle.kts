import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    kotlin("jvm")
    alias(libs.plugins.dokka)
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
    jvmToolchain(25)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
    }
}

dokka {
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        reportUndocumented.set(true)
        jdkVersion.set(25)
    }
}

dependencies {
    api(project(":mystem4j-runtime"))

    constraints {
        // BCV 0.18.2 still requests ASM 9.6, which cannot read Java 25 class files.
        add("bcv-rt-jvm-cp", libs.asm.core) {
            because("API validation must read Java 25 bytecode")
        }
        add("bcv-rt-jvm-cp", libs.asm.tree) {
            because("API validation must read Java 25 bytecode")
        }
    }

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
    // Standard Dokka HTML preserves Kotlin signatures and working navigation in the Javadoc artifact.
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

// Both plugins target the same archive path. The published Java component's
// sourcesJar includes Java and Kotlin; the Kotlin task must not overwrite it.
tasks.named("kotlinSourcesJar") {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
