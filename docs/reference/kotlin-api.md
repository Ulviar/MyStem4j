# Kotlin API Reference

Package: `io.github.ulviar.mystem4j.kotlin`

Artifact: `io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0`

`mystem4j-kotlin` brings `mystem4j-runtime` transitively.

## Client DSL

```kotlin
fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient
```

`MystemClientDsl` maps to `MystemClientBuilder`:

| Kotlin DSL | Java builder |
| --- | --- |
| `executable(Path)` | `executable(Path)` |
| `options(MystemOptions)` | `options(MystemOptions)` |
| `searchPath(Boolean)` | `searchPath(boolean)` |
| `requestTimeout(Duration)` | `requestTimeout(Duration)` |
| `idleTimeout(Duration)` | `idleTimeout(Duration)` |
| `session()` | `session()` |
| `pooled()` | `pooled()` |
| `pooled(MystemPoolOptions)` | `pooled(MystemPoolOptions)` |
| `pooled { ... }` | `pooled(Consumer<MystemPoolOptions.Builder>)` |
| `maxRequestChars(Int)` | `maxRequestChars(int)` |
| `maxRequestBytes(Int)` | `maxRequestBytes(int)` |
| `maxResponseChars(Int)` | `maxResponseChars(int)` |
| `maxResponseBytes(Int)` | `maxResponseBytes(int)` |
| `includeInputInDiagnostics(Boolean)` | `includeInputInDiagnostics(boolean)` |

Defaults are the Java runtime defaults. See the [Runtime API reference](runtime-api.md).

## Options DSL

```kotlin
fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions
```

`MystemOptionsDsl` maps to `MystemOptions.Builder` and exposes the same MyStem CLI
options as the Java API.

Example:

```kotlin
val options = mystemOptions {
    grammarInfo()
    disambiguate()
    format(MystemOutputFormat.JSON)
}
```

## Extensions

```kotlin
fun String.analyzeWith(client: MystemClient): MystemRawResult
fun Path.analyzeWith(client: MystemClient): MystemFileContentResult
```

`String.analyzeWith` calls `client.analyze(string)`. `Path.analyzeWith` calls
`client.analyzeFile(path)` and captures stdout as a string.
