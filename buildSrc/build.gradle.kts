plugins {
    `java-gradle-plugin`
}

dependencyLocking {
    lockAllConfigurations()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// The root build must not use untested verification logic.
tasks.jar {
    dependsOn(tasks.test)
}

gradlePlugin {
    plugins {
        create("mystem4jJavaConventions") {
            id = "io.github.ulviar.mystem4j.java-conventions"
            implementationClass = "io.github.ulviar.mystem4j.buildlogic.Mystem4jJavaConventionsPlugin"
        }
        create("mystem4jPublishingConventions") {
            id = "io.github.ulviar.mystem4j.publishing-conventions"
            implementationClass = "io.github.ulviar.mystem4j.buildlogic.Mystem4jPublishingConventionsPlugin"
        }
    }
}
