# Getting Started

This tutorial prepares a MyStem executable with the Gradle plugin and sends one request through the runtime.

## 1. Check Dependency Access

The runtime depends on `com.github.ulviar:icli:0.1.0`. Until iCLI is available from a public Maven repository, configure one of these before a clean build:

- publish iCLI `0.1.0` to Maven Local;
- or provide GitHub Packages credentials through `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`.

## 2. Configure the Gradle Plugin

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "<version>"
}

dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:<version>")
}

mystem4j {
    version.set("3.1")
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}
```

`download` and `acceptYandexMystemLicense` are separate opt-ins. The plugin will not download MyStem unless both are enabled.

## 3. Prepare and Probe MyStem

```bash
./gradlew mystemProbe
```

The plugin downloads the platform archive, stores it under `build/mystem/downloads`, extracts the executable under `build/mystem/bin/<platform>`, and runs a JSON smoke request.

## 4. Use the Runtime

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.nio.file.Path;

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/prepared/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
    System.out.println(result.output());
}
```

For tests configured through `configureTests.set(true)`, the plugin sets `mystem4j.executable` and `MYSTEM_PATH` for Gradle `Test` tasks.
