plugins {
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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
