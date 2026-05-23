# Gradle Plugin Reference

Plugin id: `io.github.ulviar.mystem4j`

Artifact: `mystem4j-gradle-plugin`

## Extension

```kotlin
mystem4j {
    version.set("3.1")
    targetOs.set("macos")
    baseUrl.set("https://download.cdn.yandex.net/mystem")
    archiveUrl.set("https://mirror.example.org/mystem-3.1-macosx.tar.gz")
    sha256.set("<expected-sha256>")
    download.set(true)
    acceptYandexMystemLicense.set(true)
    configureTests.set(true)
    prepareDistribution.set(false)
    distributionDirectory.set(layout.buildDirectory.dir("mystem/distribution"))
}
```

| Property | Default | Description |
| --- | --- | --- |
| `version` | `3.1` | supported MyStem version |
| `targetOs` | current OS | `linux`, `macos`, or `windows` |
| `baseUrl` | official MyStem CDN | base URL used when `archiveUrl` is unset |
| `archiveUrl` | unset | full archive URL override |
| `sha256` | unset | optional expected archive checksum |
| `download` | `false` | explicit opt-in for network download |
| `acceptYandexMystemLicense` | `false` | explicit license acceptance for download |
| `configureTests` | `false` | wire prepared executable into Gradle `Test` tasks |
| `prepareDistribution` | `false` | opt-in copy for application distribution |
| `distributionDirectory` | `build/mystem/distribution` | target directory for distribution copy |

Review the MyStem license before enabling `acceptYandexMystemLicense`: <https://yandex.ru/legal/mystem/ru/>.

## Tasks

| Task | Purpose |
| --- | --- |
| `mystemDownload` | downloads the platform archive into `build/mystem/downloads` |
| `mystemExtract` | extracts the executable into `build/mystem/bin/<platform>` |
| `mystemProbe` | runs a JSON smoke request against the prepared executable |
| `mystemPrepareTestRuntime` | writes test runtime properties and supplies the executable path |
| `mystemPrepareDistribution` | copies the executable into `distributionDirectory` when enabled |

## Supported Archives

| Target OS | Archive | Executable |
| --- | --- | --- |
| `linux` | `mystem-3.1-linux-64bit.tar.gz` | `mystem` |
| `macos` | `mystem-3.1-macosx.tar.gz` | `mystem` |
| `windows` | `mystem-3.1-win-64bit.zip` | `mystem.exe` |

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

If `sha256` is provided, the archive content must match it.
