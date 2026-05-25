# Gradle Plugin Reference

Plugin id: `io.github.ulviar.mystem4j`

Maven artifact: `io.github.ulviar.mystem4j:mystem4j-gradle-plugin:0.1.0`

```kotlin
plugins {
    id("io.github.ulviar.mystem4j") version "0.1.0"
}
```

```groovy
plugins {
    id 'io.github.ulviar.mystem4j' version '0.1.0'
}
```

## Extension

```kotlin
mystem4j {
    version.set("3.1")
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
}
```

| Property | Default | Description |
| --- | --- | --- |
| `version` | `3.1` | supported MyStem version |
| `targetOs` | current OS | `linux`, `macos`, or `windows` |
| `baseUrl` | `https://download.cdn.yandex.net/mystem` | base URL used when `archiveUrl` is unset |
| `archiveUrl` | unset | full archive URL override |
| `sha256` | built in for official archives | expected archive checksum |
| `maxArchiveBytes` | `104857600` | maximum downloaded archive size |
| `maxProbeOutputBytes` | `65536` | maximum captured stdout/stderr bytes for `mystemProbe` |
| `cacheDirectory` | Gradle user home `caches/mystem4j` | shared checksum cache for downloaded archives |
| `download` | `false` | explicit opt-in for network download |
| `acceptYandexMystemLicense` | `false` | explicit confirmation that the project accepts the Yandex MyStem license before download |
| `configureTests` | `false` | wire prepared executable into Gradle `Test` tasks |
| `prepareDistribution` | `false` | opt-in copy for application distribution |
| `distributionDirectory` | `build/mystem/distribution` | target directory for distribution copy |

Review the MyStem license before enabling `acceptYandexMystemLicense`:
<https://yandex.ru/legal/mystem/ru/>.

Official archive URLs are built by appending the archive name to `baseUrl`. Custom
`http` and `https` `archiveUrl` values must set `sha256`.

## Tasks

| Task | Purpose |
| --- | --- |
| `mystemDownload` | downloads or reuses the platform archive and writes it into `build/mystem/downloads` |
| `mystemExtract` | extracts the executable into `build/mystem/bin/<platform>` |
| `mystemProbe` | runs a JSON smoke request against the prepared executable |
| `mystemPrepareTestRuntime` | prepares the executable path for Gradle `Test` tasks |
| `mystemPrepareDistribution` | copies the executable into `distributionDirectory` when enabled |

## Supported Archives

The checksum values are embedded in MyStem4j `0.1.0` and are used to verify official
downloads.

| Target OS | Archive | Executable | SHA-256 |
| --- | --- | --- | --- |
| `linux` | `mystem-3.1-linux-64bit.tar.gz` | `mystem` | `4696f4ea8ce3ecda24ef5e8dfe7e4b16cfa5f1844edfcca31c34d636b73c0a62` |
| `macos` | `mystem-3.1-macosx.tar.gz` | `mystem` | `346e576ada01cc7c63414a9d91f6733bd418f496f073d13a4812aec3628e5693` |
| `windows` | `mystem-3.1-win-64bit.zip` | `mystem.exe` | `03cdbe2c01661eb449e84771817096161203553fca4bca934dc17f1bc9e53bc8` |

## Test Wiring

When `configureTests` is enabled, each Gradle `Test` task gets:

- `dependsOn(mystemPrepareTestRuntime)`;
- system property `mystem4j.executable`;
- environment variable `MYSTEM_PATH`;
- Gradle inputs for the executable path and executable file.

## Cache Metadata

`mystemDownload` writes a metadata sidecar next to the project-local archive. The
project-local archive is reused only when these values match:

- version;
- archive URL;
- expected sha256 value.

The archive content must match the expected checksum whenever a checksum is
configured or supplied by built-in distribution metadata.

When a checksum is available, downloaded archives are also stored under
`cacheDirectory` with a file lock. This cache is shared between builds and projects,
while extraction outputs remain under the current project's `build` directory.
