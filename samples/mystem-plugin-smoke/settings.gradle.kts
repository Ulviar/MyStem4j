pluginManagement {
    repositories {
        if (providers.gradleProperty("mystem4j.useMavenLocal").map(String::toBoolean).orElse(false).get()) {
            mavenLocal()
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("mystem4j.useMavenLocal").map(String::toBoolean).orElse(false).get()) {
            mavenLocal()
        }
        mavenCentral()
    }
}

rootProject.name = "mystem-plugin-smoke"
