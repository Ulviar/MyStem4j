# Troubleshooting

## Gradle Cannot Resolve The Plugin Or Artifacts

Check that your build uses repositories that contain the release:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Normal users should declare MyStem4j modules only. Runtime implementation libraries
are transitive dependencies of the published artifacts.

## MyStem Download Is Disabled

`mystemDownload` fails until both opt-ins are set:

```kotlin
mystem4j {
    download.set(true)
    acceptYandexMystemLicense.set(true)
}
```

Set `acceptYandexMystemLicense` to `true` only after reviewing and accepting the
Yandex MyStem license for your project.

## Executable Cannot Be Found

The runtime resolves MyStem in this order:

1. explicit `Mystem.builder().executable(path)`;
2. `mystem4j.executable` system property;
3. `MYSTEM_PATH` environment variable;
4. `PATH`, when `searchPath(true)` is enabled.

When using the Gradle plugin for tests, set `configureTests.set(true)` and read
`System.getProperty("mystem4j.executable")` in test code.

## MyStem reports bad CPU type on Apple Silicon

The plugin's macOS MyStem 3.1 executable is built for Intel. On an Apple Silicon
Mac, starting it without Rosetta fails with `Bad CPU type in executable` (or
`error=86` from Java).

Install Rosetta using macOS, then retry the same executable. From Terminal, run:

```bash
softwareupdate --install-rosetta
```

Review and accept Apple's license when prompted. This is a system installation;
the Gradle plugin does not perform it. After installation, verify the binary with
`/path/to/mystem --version`, or rerun `./gradlew mystemProbe` when using the plugin.
An ARM Java runtime can keep running natively while MyStem runs through Rosetta.

## Checksum Mismatch

For official archives, the plugin uses built-in SHA-256 checksums. A mismatch means
the downloaded file does not match the checksum embedded in MyStem4j. Delete the
project-local `build/mystem/downloads` directory and retry. If you use a mirror,
verify that `archiveUrl` and `sha256` describe the same archive.

## Reusable Or Pooled Client Rejects Multiline Text

Reusable and pooled clients use JSON-line framing: one input line, one JSON output
line. They reject raw `\r` and `\n` characters. Use one-shot mode for multiline
text, or prepare input with `MystemTextPreprocessor.prepareJsonLine` and parse the
result with `MystemJsonParser.parse(MystemPreparedText, json)`.

## Parsed Output Has Unknown Offsets

If `MystemJsonParser` reports `UNMATCHED_TOKEN`, inspect `document.issues()`.
The default search tokenizer still emits offset-safe fallback tokens from the
original text. Use `MystemUnmatchedTokenPolicy.FAIL` when the application should
reject such documents instead. When the original text can contain unusual Unicode
values, run `MystemTextPreprocessor` before MyStem and parse with the prepared-text
overload.

## Lucene Analyzer Is Slow

For indexing, use a pooled MyStem client and reuse the analyzer for the indexing
job. One-shot mode starts one native process per request and is usually too slow
for large indexing workloads.
