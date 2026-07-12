plugins {
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
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
