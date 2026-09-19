# Kotlin API Reference

Package: `io.github.ulviar.mystem4j.kotlin`

Artifact: `io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0`

`mystem4j-kotlin` brings `mystem4j-runtime` transitively.

Built with Kotlin 2.4.20 for Java 25. Applications require Java 25 or newer.

## Client DSL

```kotlin
fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient
```

`MystemClientDsl` maps to `MystemClientBuilder`:

| Kotlin DSL | Java builder |
| --- | --- |
| `executable(Path)` | `executable(Path)` |
| `executable(String)` | `executable(Path)` |
| `executable(File)` | `executable(Path)` |
| `options(MystemOptions)` | `options(MystemOptions)` |
| `options { ... }` | `options(MystemOptions)` |
| `searchPath(Boolean)` | `searchPath(boolean)` |
| `requestTimeout(Duration)` | `requestTimeout(Duration)` |
| `requestTimeout(kotlin.time.Duration)` | `requestTimeout(Duration)` |
| `idleTimeout(Duration)` | `idleTimeout(Duration)` |
| `idleTimeout(kotlin.time.Duration)` | `idleTimeout(Duration)` |
| `session()` | `session()` |
| `pooled()` | `pooled()` |
| `pooled(MystemPoolOptions)` | `pooled(MystemPoolOptions)` |
| `pooled { ... }` | `pooled(Consumer<MystemPoolOptions.Builder>)` |
| `maxRequestChars(Int)` | `maxRequestChars(int)` |
| `maxRequestBytes(Int)` | `maxRequestBytes(int)` |
| `maxResponseChars(Int)` | `maxResponseChars(int)` |
| `maxResponseBytes(Int)` | `maxResponseBytes(int)` |
| `includeInputInDiagnostics(Boolean)` | `includeInputInDiagnostics(boolean)` |

Defaults, validation, lifecycle, and exceptions are the Java runtime contracts.
Clients default to one-shot execution, JSON, UTF-8, a three-second execution timeout,
and disabled process I/O inactivity timeout. Close clients with `use { ... }`.
Session and pool modes require JSON, reject `newLineEachWord(true)`, and accept
text without CR/LF only. File requests always use separate one-shot processes.

`maxRequestChars` and `maxResponseChars` count UTF-16 code units. Input byte limits
use the configured encoding and exclude the added protocol newline; output limits
include line endings. Java and Kotlin duration overloads apply the same validation.
See [runtime limits](runtime-api.md#limits-and-timeouts) and
[request statistics](runtime-api.md#request-statistics) for exact boundaries.

## Options DSL

```kotlin
fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions
```

`MystemOptionsDsl` maps to `MystemOptions.Builder` and exposes the same MyStem CLI
options as the Java API. All flags are disabled initially; calling a boolean DSL
method without an argument enables that flag. `mergeWordForms()` requires
`grammarInfo()` and `sentenceMarkers()` requires `copyInput()`.

Example:

```kotlin
val options = mystemOptions {
    grammarInfo()
    disambiguate()
    format(MystemOutputFormat.JSON)
}

val client = mystemClient {
    executable("/path/to/mystem")
    options {
        grammarInfo()
        disambiguate()
    }
}
```

## Pool DSL

The `pooled { ... }` block uses `MystemPoolOptionsDsl`. It exposes pool sizing,
timeouts, worker rotation, and replenishment. Timeout and worker-age methods
accept Java or Kotlin durations.

## Extensions

```kotlin
fun String.analyzeWith(client: MystemClient): MystemRawResult
fun Path.analyzeWith(client: MystemClient): MystemFileContentResult
fun File.analyzeWith(client: MystemClient): MystemFileContentResult
```

`String.analyzeWith` calls `client.analyze(string)`. `Path.analyzeWith` calls
`client.analyzeFile(path)` and captures stdout as a string. `File.analyzeWith`
delegates to the `Path` extension.
