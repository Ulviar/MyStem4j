# Use Kotlin DSL

Add the Kotlin helper artifact when Kotlin code should configure runtime clients or options directly.

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-kotlin:0.1.0")
}
```

## Build Options

```kotlin
val options = mystemOptions {
    grammarInfo()
    disambiguate()
    englishGrammemes()
    format(MystemOutputFormat.JSON)
}
```

## Build a Client

```kotlin
mystemClient {
    executable(Path.of("/path/to/mystem"))
    options(options)
    requestTimeout(Duration.ofSeconds(2))
}.use { client ->
    val raw = "Мама мыла раму.".analyzeWith(client)
}
```

## Use a Pool

```kotlin
mystemClient {
    executable(Path.of("/path/to/mystem"))
    pooled {
        maxSize(4)
        warmupSize(1)
    }
}.use { client ->
    val raw = client.analyze("Мама мыла раму.")
}
```
