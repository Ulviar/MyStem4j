# Kotlin API Reference

Package: `io.github.ulviar.mystem4j.kotlin`

Artifact: `mystem4j-kotlin`

## Client DSL

```kotlin
fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient
```

`MystemClientDsl` maps to `MystemClientBuilder`:

- `executable(Path)`;
- `options(MystemOptions)`;
- `searchPath(Boolean)`;
- `requestTimeout(Duration)`;
- `idleTimeout(Duration)`;
- `session()`;
- `pooled()`;
- `pooled(MystemPoolOptions)`;
- `pooled { ... }`;
- `maxRequestChars(Int)`;
- `maxRequestBytes(Int)`;
- `maxResponseChars(Int)`;
- `maxResponseBytes(Int)`;
- `includeInputInDiagnostics(Boolean)`.

## Options DSL

```kotlin
fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions
```

`MystemOptionsDsl` maps to `MystemOptions.Builder` and exposes the same MyStem CLI options as the Java API.

## Extensions

```kotlin
fun String.analyzeWith(client: MystemClient): MystemRawResult
fun Path.analyzeWith(client: MystemClient): MystemFileContentResult
```
