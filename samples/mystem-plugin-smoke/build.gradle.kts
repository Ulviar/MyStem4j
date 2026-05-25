plugins {
    id("io.github.ulviar.mystem4j") version "0.1.0"
}

mystem4j {
    version.set("3.1")
    download.set(providers.gradleProperty("mystem4j.download").map(String::toBoolean).orElse(false))
    acceptYandexMystemLicense.set(
        providers.gradleProperty("mystem4j.acceptYandexMystemLicense").map(String::toBoolean).orElse(false)
    )
    configureTests.set(false)
}
