# Prepare MyStem With Gradle

Use `mystem4j-gradle-plugin` when a build needs a known MyStem binary for tests, smoke checks, or application distribution preparation.

## Download MyStem

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

The plugin supports MyStem `3.1` only. It selects one of the official archives for `linux`, `macos`, or `windows`.
Official archives are verified with built-in SHA-256 checksums.

## Use an Internal Mirror

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
    archiveUrl.set("https://mirror.example.org/mystem-3.1-linux-64bit.tar.gz")
    sha256.set("<expected-sha256>")
}
```

Custom remote archives must set `sha256`. The download task verifies the archive before replacing the cached file.

## Wire MyStem Into Tests

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}
```

When enabled, every Gradle `Test` task depends on `mystemPrepareTestRuntime` and receives:

- JVM system property `mystem4j.executable`;
- environment variable `MYSTEM_PATH`;
- an input property and executable input file for Gradle up-to-date checks.

## Prepare a Distribution Copy

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

This copies the prepared executable into the configured directory. It does not add MyStem to the published runtime artifact.

## Cache Behavior

The plugin uses two cache layers:

- archive: `build/mystem/downloads/<archive>`;
- metadata sidecar: `build/mystem/downloads/<archive>.metadata.properties`;
- executable: `build/mystem/bin/<platform>/<mystem executable>`.

The project-local archive is reused only when version, URL, and expected checksum metadata match. `gradle clean` removes
this layer.

When a checksum is configured or built in, the downloaded archive is also reused from `cacheDirectory`, which defaults to
Gradle user home `caches/mystem4j`. Override it only when the build needs an isolated cache:

```kotlin
mystem4j {
    cacheDirectory.set(layout.projectDirectory.dir(".gradle/mystem4j-cache"))
}
```
