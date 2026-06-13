# Getting started

This tutorial builds the smallest useful Gradle setup: download MyStem, probe it,
and send one request through `mystem4j-runtime`.

## 1. Configure plugin and dependency repositories

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

Use both repositories so Gradle can resolve the plugin and Maven artifacts.

## 2. Configure the build

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

`acceptYandexMystemLicense` is a separate opt-in; set it to `true` only after
reviewing and accepting <https://yandex.ru/legal/mystem/ru/> for your project.

`configureTests` wires the prepared executable into every Gradle `Test` task through
the `mystem4j.executable` system property and the `MYSTEM_PATH` environment variable.

## 3. Add a smoke test

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

## 4. Run it

```bash
./gradlew test
```

On the first run, the plugin downloads and verifies MyStem, prepares the executable,
and runs the test.

Expected output shape, formatted here for readability:

```text
[
  {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"Мама"},
  {"text":" "},
  {"analysis":[{"lex":"мыть","gr":"V,..."}],"text":"мыла"},
  {"text":" "},
  {"analysis":[{"lex":"рама","gr":"S,..."}],"text":"раму"},
  {"text":"."}
]
```

The exact grammar string comes from MyStem data; for this smoke test, check that a
raw JSON line is returned.

## Next steps

- Use [runtime clients](../how-to/use-runtime-clients.md) directly when raw MyStem output is enough.
- Use [model parsing](../how-to/parse-mystem-output.md) when you need Java objects, lemmas, grammar, and offsets.
- Use [Lucene integration](../how-to/use-lucene-analyzer.md) when MyStem should be part of an analyzer pipeline.
- Use [Kotlin DSL](../how-to/use-kotlin-dsl.md) from Kotlin code.
