# Use Kotlin DSL

Use `mystem4j-kotlin` when Kotlin code should configure runtime clients or options
directly.

## Add The Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0")
}
```

`mystem4j-kotlin` brings `mystem4j-runtime` transitively.

## Build Options

```kotlin
import io.github.ulviar.mystem4j.MystemOutputFormat
import io.github.ulviar.mystem4j.kotlin.mystemOptions

val options = mystemOptions {
    grammarInfo()
    disambiguate()
    format(MystemOutputFormat.JSON)
}
```

Call `englishGrammemes()` only when you want MyStem grammar tags such as part of
speech and case names in English instead of MyStem's default Russian abbreviations.

## Build A Client

```kotlin
import io.github.ulviar.mystem4j.kotlin.analyzeWith
import io.github.ulviar.mystem4j.kotlin.mystemClient
import java.nio.file.Path
import java.time.Duration

mystemClient {
    executable(Path.of("/path/to/mystem"))
    options(options)
    requestTimeout(Duration.ofSeconds(2))
}.use { client ->
    val raw = "Мама мыла раму.".analyzeWith(client)
    println(raw.output())
}
```

## Use A Pool

```kotlin
mystemClient {
    executable(Path.of("/path/to/mystem"))
    options(options)
    pooled {
        maxSize(4)
        warmupSize(1)
    }
}.use { client ->
    val raw = client.analyze("Мама мыла раму.")
    println(raw.output())
}
```

The Kotlin DSL maps directly to the Java runtime builder. Use the
[Runtime API reference](../reference/runtime-api.md) for option defaults, limits,
timeouts, and exception types.
