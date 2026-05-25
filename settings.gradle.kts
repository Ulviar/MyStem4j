pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        if (providers.gradleProperty("mystem4j.useMavenLocal").map(String::toBoolean).orElse(false).get()) {
            mavenLocal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("mystem4j.useMavenLocal").map(String::toBoolean).orElse(false).get()) {
            mavenLocal()
        }
        val githubPackagesUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val githubPackagesToken = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
        if (githubPackagesUser.isPresent && githubPackagesToken.isPresent) {
            maven {
                name = "GitHubPackagesIcli"
                url = uri("https://maven.pkg.github.com/Ulviar/iCLI")
                credentials {
                    username = githubPackagesUser.get()
                    password = githubPackagesToken.get()
                }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MyStem4j"

include("mystem4j-runtime")
include("mystem4j-model")
include("mystem4j-tokenization")
include("mystem4j-lucene")
include("mystem4j-kotlin")
include("mystem4j-gradle-plugin")
