# Use Kotlin DSL

Use `mystem4j-kotlin` when Kotlin code should configure runtime clients or options
directly.

## Add the module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0")
}
```

`mystem4j-kotlin` brings `mystem4j-runtime` transitively.

## Build options

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

## Build a client

```kotlin
import io.github.ulviar.mystem4j.kotlin.analyzeWith
import io.github.ulviar.mystem4j.kotlin.mystemClient
import kotlin.time.Duration.Companion.seconds

mystemClient {
    executable("/path/to/mystem")
    options {
        grammarInfo()
        disambiguate()
    }
    requestTimeout(2.seconds)
}.use { client ->
    val raw = "Мама мыла раму.".analyzeWith(client)
    println(raw.output())
}
```

## Use a pool

```kotlin
mystemClient {
    executable("/path/to/mystem")
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
