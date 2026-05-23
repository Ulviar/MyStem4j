pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackagesIcli"
            url = uri("https://maven.pkg.github.com/Ulviar/iCLI")
            credentials {
                username =
                    providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MyStem4j"

include("mystem4j-runtime")
include("mystem4j-kotlin")
include("mystem4j-gradle-plugin")
