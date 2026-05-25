import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
}

val mystem4jVersion = providers.gradleProperty("mystem4j.version").orElse("0.1.0-SNAPSHOT")

allprojects {
    group = "io.github.ulviar.mystem4j"
    version = mystem4jVersion.get()
}

subprojects {
    plugins.withType<JavaPlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
            options.encoding = "UTF-8"
        }

        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
