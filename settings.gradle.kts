pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
include("mystem4j-benchmarks")

include("mystem4j-http-client")
include("mystem4j-http-server")
