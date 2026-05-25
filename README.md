# MyStem4j

MyStem4j is a Java/Kotlin library for calling Yandex MyStem from JVM applications.
MyStem is a native command-line morphological analyzer for Russian text: it can return
lemmas, grammar tags, and token text. MyStem4j adds a typed JVM API, optional JSON
postprocessing, search-token preparation, Lucene integration, Kotlin helpers, and a
Gradle plugin that can prepare the native MyStem executable for builds and tests.

Use MyStem4j when you need one of these paths:

- run MyStem and read its raw JSON, XML, or text output from Java;
- parse MyStem JSON into Java model objects with original text offsets;
- build search tokens from MyStem lemmas before a custom search pipeline;
- use MyStem inside a Lucene `Analyzer` or `Tokenizer`;
- prepare the MyStem binary during a Gradle build.

## Requirements

- Java 21 or newer.
- MyStem 3.1. MyStem4j does not bundle the native MyStem executable.
- A project decision to accept the Yandex MyStem license before downloading MyStem
  through the Gradle plugin.

MyStem links:

- MyStem documentation: <https://yandex.ru/dev/mystem/>
- MyStem license: <https://yandex.ru/legal/mystem/ru/>

Normal users declare MyStem4j modules only. Runtime implementation dependencies are
transitive dependencies of the published artifacts.

## Modules

| Need | Module |
| --- | --- |
| Run MyStem and receive raw output | `mystem4j-runtime` |
| Parse existing MyStem JSON into Java objects | `mystem4j-model` |
| Convert parsed model objects into search tokens | `mystem4j-tokenization` |
| Use MyStem in Lucene analysis | `mystem4j-lucene` |
| Configure runtime clients from Kotlin | `mystem4j-kotlin` |
| Download and prepare the native MyStem executable in Gradle | `mystem4j-gradle-plugin` |

Start with the highest-level module that matches your task. For example,
`mystem4j-lucene` brings the runtime and tokenization APIs it exposes, so most
Lucene applications do not need to declare those modules separately.

## Quick Start

The shortest end-to-end setup is a Gradle test. The plugin downloads MyStem, probes
it, and passes the executable path to the test as `mystem4j.executable`.

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "0.1.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

mystem4j {
    version.set("3.1")
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}

tasks.test {
    useJUnitPlatform()
}
```

Set `acceptYandexMystemLicense` to `true` only after reviewing and accepting the
Yandex MyStem license for your project.

```java
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MystemSmokeTest {
    @Test
    void analyzesRussianText() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .disambiguate(true)
                        .build())
                .build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");
            System.out.println(result.output());
            assertTrue(result.output().contains("\"lex\":\"мама\""));
        }
    }
}
```

Run:

```bash
./gradlew test
```

The output is one MyStem JSON array line. Exact grammar tags can change with MyStem
dictionary data, but the result should contain a lemma such as `мама`.

## Documentation

- [Getting started](docs/tutorials/getting-started.md)
- [Choose modules](docs/how-to/choose-modules.md)
- [Prepare MyStem with Gradle](docs/how-to/prepare-mystem-with-gradle.md)
- [Use runtime clients](docs/how-to/use-runtime-clients.md)
- [Parse MyStem output](docs/how-to/parse-mystem-output.md)
- [Prepare search tokens](docs/how-to/prepare-search-tokens.md)
- [Use Lucene analyzer](docs/how-to/use-lucene-analyzer.md)
- [Use Kotlin DSL](docs/how-to/use-kotlin-dsl.md)
- [Troubleshooting](docs/how-to/troubleshooting.md)
- [API stability](docs/reference/api-stability.md)
- [Glossary](docs/reference/glossary.md)
- [Full documentation index](docs/README.md)
- [Changelog](CHANGELOG.md)

## License

MyStem4j is licensed under the Apache License, Version 2.0. The native MyStem
binary is licensed separately by Yandex and is not bundled into MyStem4j artifacts.
