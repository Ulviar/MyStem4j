# Prepare MyStem With Gradle

Use the Gradle plugin when your build should download, verify, extract, probe, or
pass a MyStem executable to tests. The Java runtime can also use a MyStem binary
that you install yourself; the plugin is only a build-time convenience.

## Apply The Plugin

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "0.1.0"
}
```

If the plugin marker is resolved from Maven Central, add `mavenCentral()` to
`pluginManagement.repositories` in `settings.gradle.kts`. The
[getting started tutorial](../tutorials/getting-started.md) shows a full project.

## Download And Probe MyStem

```kotlin
mystem4j {
    version.set("3.1")
    download.set(true)
    acceptYandexMystemLicense.set(true)
}
```

```bash
./gradlew mystemProbe
```

Set `acceptYandexMystemLicense` to `true` only after reviewing and accepting
<https://yandex.ru/legal/mystem/ru/> for your project. The plugin will not download
MyStem unless both `download` and `acceptYandexMystemLicense` are enabled.

The plugin supports MyStem `3.1` official archives for `linux`, `macos`, and
`windows`. The Windows and Linux archives are 64-bit archives. The macOS archive is
the single macOS archive published by Yandex for MyStem 3.1; there is no separate
ARM archive in the plugin metadata.

## Wire MyStem Into Tests

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}
```

When `configureTests` is enabled, every Gradle `Test` task depends on
`mystemPrepareTestRuntime` and receives:

- JVM system property `mystem4j.executable`;
- environment variable `MYSTEM_PATH`.

Test code can read the path directly:

```java
Path executable = Path.of(System.getProperty("mystem4j.executable"));
```

Use this mode for integration tests and smoke tests that need a real MyStem binary.

## Prepare A Distribution Copy

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
    prepareDistribution.set(true)
    distributionDirectory.set(layout.buildDirectory.dir("dist/mystem"))
}
```

```bash
./gradlew mystemPrepareDistribution
```

This copies the prepared executable into the configured directory. It does not add
MyStem to MyStem4j artifacts, and it does not decide whether your application may
redistribute MyStem. Make that decision from the Yandex MyStem license and your
application's distribution model. A common production pattern is to configure the
application with an explicit executable path or environment variable and keep the
native binary outside the Java artifact.

## Use An Internal Mirror

Use a mirror only when your organization does not allow direct downloads from the
official Yandex URL.

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
    archiveUrl.set("https://mirror.example.org/mystem-3.1-linux-64bit.tar.gz")
    sha256.set("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
}
```

Custom remote archives must set `sha256`. The plugin verifies the archive before
using it.

## Cache Behavior

The plugin keeps a project-local archive under `build/mystem/downloads` and the
extracted executable under `build/mystem/bin/<platform>`. `gradle clean` removes
these project-local files.

When a checksum is known, the downloaded archive can also be reused from
`cacheDirectory`, which defaults to Gradle user home `caches/mystem4j`. Override it
only when a build needs an isolated cache:

```kotlin
mystem4j {
    cacheDirectory.set(layout.projectDirectory.dir(".gradle/mystem4j-cache"))
}
```

See the [Gradle plugin reference](../reference/gradle-plugin.md) for all properties,
tasks, archive names, and checksum values.
