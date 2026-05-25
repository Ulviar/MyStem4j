# Gradle Plugin Reference

Plugin id: `io.github.ulviar.mystem4j`

Artifact: `mystem4j-gradle-plugin`

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
| `baseUrl` | official MyStem CDN | base URL used when `archiveUrl` is unset |
| `archiveUrl` | unset | full archive URL override |
| `sha256` | built in for official archives | expected archive checksum |
| `maxArchiveBytes` | `104857600` | maximum downloaded archive size |
| `maxProbeOutputBytes` | `65536` | maximum captured stdout/stderr bytes for `mystemProbe` |
| `download` | `false` | explicit opt-in for network download |
| `acceptYandexMystemLicense` | `false` | explicit license acceptance for download |
| `configureTests` | `false` | wire prepared executable into Gradle `Test` tasks |
| `prepareDistribution` | `false` | opt-in copy for application distribution |
| `distributionDirectory` | `build/mystem/distribution` | target directory for distribution copy |

Review the MyStem license before enabling `acceptYandexMystemLicense`: <https://yandex.ru/legal/mystem/ru/>.

Official archives use built-in SHA-256 values. Custom `http` and `https` `archiveUrl` values must set `sha256`.

## Tasks

| Task | Purpose |
| --- | --- |
| `mystemDownload` | downloads the platform archive into `build/mystem/downloads` |
| `mystemExtract` | extracts the executable into `build/mystem/bin/<platform>` |
| `mystemProbe` | runs a JSON smoke request against the prepared executable |
| `mystemPrepareTestRuntime` | writes test runtime properties and supplies the executable path |
| `mystemPrepareDistribution` | copies the executable into `distributionDirectory` when enabled |

## Supported Archives

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

`mystemDownload` writes a metadata sidecar next to the archive. The cached archive is reused only when these values match:

- version;
- archive URL;
- expected sha256 value.

The archive content must match the expected checksum whenever a checksum is configured or supplied by the built-in distribution metadata.
