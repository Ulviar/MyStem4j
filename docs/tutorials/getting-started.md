# Getting Started

This tutorial creates a minimal Gradle project that downloads MyStem, runs a smoke
probe, and sends one request through `mystem4j-runtime`.

## 1. Configure Plugin And Dependency Repositories

Create `settings.gradle.kts`:

```kotlin
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

rootProject.name = "mystem4j-example"
```

`gradlePluginPortal()` is enough when the Gradle plugin is published there.
`mavenCentral()` lets Gradle resolve the plugin marker when it is published with
the Maven artifacts.

## 2. Configure The Build

Create `build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "0.1.0"
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

`download` allows the plugin to fetch the MyStem archive. `acceptYandexMystemLicense`
is a separate opt-in: set it to `true` only after reviewing and accepting
<https://yandex.ru/legal/mystem/ru/> for your project.

`configureTests` wires the prepared executable into every Gradle `Test` task through
the `mystem4j.executable` system property and the `MYSTEM_PATH` environment variable.

## 3. Add A Smoke Test

Create `src/test/java/example/MystemSmokeTest.java`:

```java
package example;

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

## 4. Run It

```bash
./gradlew test
```

The first run downloads the platform archive, verifies its checksum, extracts the
executable under `build/mystem/bin/<platform>`, probes it, and then runs the test.

Expected output shape:

```json
[{"analysis":[{"lex":"мама","gr":"S,..."}],"text":"Мама"}, ...]
```

The exact grammar string is MyStem data. The important part for this tutorial is
that the runtime returns one raw MyStem JSON line.

## Next Steps

- Use [runtime clients](../how-to/use-runtime-clients.md) directly when raw MyStem output is enough.
- Use [model parsing](../how-to/parse-mystem-output.md) when you need Java objects, lemmas, grammar, and offsets.
- Use [Lucene integration](../how-to/use-lucene-analyzer.md) when MyStem should be part of an analyzer pipeline.
- Use [Kotlin DSL](../how-to/use-kotlin-dsl.md) from Kotlin code.
