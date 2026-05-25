# MyStem4j

Java/Kotlin libraries for using Yandex MyStem from JVM applications and Lucene pipelines.

This repository currently contains the lower layers of the project: a runtime over the MyStem CLI, model postprocessing, search-token preparation, Lucene integration, Kotlin helpers, and a Gradle plugin that prepares the native MyStem binary for local builds and tests.

## Modules

- `mystem4j-runtime` - MyStem CLI runtime: one-shot requests, reusable JSON-line sessions, pooled sessions, file requests, executable probing, typed options, and runtime exceptions.
- `mystem4j-model` - MyStem JSON postprocessing: parsed tokens, analyses, grammar strings, offsets, and Unicode text preparation.
- `mystem4j-tokenization` - search-token preparation above model objects: forms, coarse token types, configurable URL/email/currency/number enrichment, and MyStem surface quirks.
- `mystem4j-lucene` - Lucene `Analyzer` and `Tokenizer` integration above runtime, model, and tokenization layers.
- `mystem4j-kotlin` - Kotlin DSL and extension helpers over the Java runtime API.
- `mystem4j-gradle-plugin` - Gradle plugin that downloads, extracts, probes, and wires a MyStem binary into test/distribution workflows.

## Important Prerequisites

MyStem4j does not bundle MyStem and does not download it from the runtime library. Downloading is handled only by the Gradle plugin and requires explicit acceptance of the Yandex MyStem license:

- MyStem docs: <https://yandex.ru/dev/mystem/>
- MyStem license: <https://yandex.ru/legal/mystem/ru/>

The runtime depends on `com.github.ulviar:icli:0.1.0`. Until iCLI is published to a public Maven repository, fresh builds need one of these:

- iCLI `0.1.0` published to Maven Local.
- GitHub Packages credentials configured through `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN`.

## Quick Start

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "<version>"
}

dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-model:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-tokenization:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:<version>") // optional Lucene integration
    implementation("io.github.ulviar.mystem4j:mystem4j-kotlin:<version>") // optional Kotlin DSL
}

mystem4j {
    version.set("3.1")
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}
```

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
}
```

```kotlin
val options = mystemOptions {
    grammarInfo()
    disambiguate()
    format(MystemOutputFormat.JSON)
}
```

## Documentation

The documentation is organized with Diataxis:

- [Documentation index](docs/README.md)
- [Getting started tutorial](docs/tutorials/getting-started.md)
- [Prepare MyStem with Gradle](docs/how-to/prepare-mystem-with-gradle.md)
- [Use runtime clients](docs/how-to/use-runtime-clients.md)
- [Parse MyStem output](docs/how-to/parse-mystem-output.md)
- [Prepare search tokens](docs/how-to/prepare-search-tokens.md)
- [Use Lucene analyzer](docs/how-to/use-lucene-analyzer.md)
- [Runtime API reference](docs/reference/runtime-api.md)
- [Model API reference](docs/reference/model-api.md)
- [Tokenization API reference](docs/reference/tokenization-api.md)
- [Lucene API reference](docs/reference/lucene-api.md)
- [Gradle plugin reference](docs/reference/gradle-plugin.md)
- [Architecture explanation](docs/explanation/architecture.md)
- [Runtime component specification](docs/specs/mystem-runtime-spec.md)
- [Model component specification](docs/specs/mystem-model-spec.md)
- [Tokenization component specification](docs/specs/mystem-tokenization-spec.md)
- [Lucene component specification](docs/specs/mystem-lucene-spec.md)

## Local Smoke Sample

The sample in `samples/mystem-plugin-smoke` resolves the plugin from Maven Local and can be used after publishing the local snapshot:

```bash
./gradlew :mystem4j-gradle-plugin:publishToMavenLocal
cd samples/mystem-plugin-smoke
../../gradlew mystemProbe \
  -Pmystem4j.download=true \
  -Pmystem4j.acceptYandexMystemLicense=true
```
