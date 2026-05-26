# Prepare MyStem with Gradle

Use the Gradle plugin when your build should download, verify, extract, probe, or
pass a MyStem executable to tests. The plugin exposes the downloaded archive and
prepared executable as Gradle providers, so your own packaging tasks can copy them
where they belong.

## Apply the plugin

```kotlin
plugins {
    java
    id("io.github.ulviar.mystem4j") version "0.1.0"
}
```

If the plugin marker is resolved from Maven Central, add `mavenCentral()` to
`pluginManagement.repositories` in `settings.gradle.kts`. The
[getting started tutorial](../tutorials/getting-started.md) shows a full project.

## Download and probe MyStem

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

## Wire MyStem into tests

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

## Use the prepared executable in packaging tasks

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
}

tasks.register<Sync>("stageMystem") {
    from(mystem4j.preparedExecutable)
    into(layout.buildDirectory.dir("staging/mystem"))
}
```

`mystem4j.preparedExecutable` is a provider backed by the `mystemExtract` task. A
`Copy`, `Sync`, Docker-context, installer-staging, or application-distribution task
that uses it gets the right task dependency without the plugin knowing how your
application is packaged.

The application still needs a runtime path: pass the staged file explicitly with
`.executable(Path.of(...))`, set `mystem4j.executable`, or set `MYSTEM_PATH` when
starting the application.

Use `mystem4j.downloadedArchive` if a custom packaging task needs the original
archive instead of the extracted executable. Use `mystem4j.executablePath` only
for APIs that need the prepared executable path as a string; use
`mystem4j.preparedExecutable` as the file input when task dependency matters.

Run `mystemProbe` separately when the prepared executable can run on the build
machine. Packaging tasks can use `preparedExecutable` without probing, which is
useful when preparing a binary for another target OS.

## Use an internal mirror

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

## Cache behavior

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
