# Use Runtime Clients

`mystem4j-runtime` exposes raw MyStem output. It does not parse JSON/XML/text into morphology model objects.

## One-Shot Text Requests

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
}
```

One-shot mode starts a new MyStem process per request. It supports `JSON`, `XML`, and `TEXT` formats.

## Reusable JSON-Line Session

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .build())
        .session()
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
}
```

Reusable mode keeps one process open. It currently supports JSON only and rejects text containing `\r` or `\n`, because framing is one input line to one JSON output line.

## Pooled Sessions

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .pooled(pool -> pool
                .maxSize(4)
                .warmupSize(1)
                .minIdle(1)
                .acquireTimeout(Duration.ofSeconds(2)))
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
}
```

Pooled clients are intended for concurrent use. Options are fixed for the whole pool.

## File Requests

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .build()) {
    MystemFileContentResult stdout = client.analyzeFile(Path.of("input.txt"));
    MystemFileResult file = client.analyzeFile(Path.of("input.txt"), Path.of("output.json"));
}
```

`analyzeFile(input, output)` uses MyStem's native `mystem [options] input output` mode and does not read the full output into JVM memory. Input and output must be different paths.

## Kotlin DSL

```kotlin
val client = mystemClient {
    executable(Path.of("/path/to/mystem"))
    options(mystemOptions {
        grammarInfo()
        disambiguate()
        format(MystemOutputFormat.JSON)
    })
    pooled {
        maxSize(4)
        warmupSize(1)
    }
}

client.use {
    val result = "Мама мыла раму.".analyzeWith(it)
}
```

## Probe an Executable

```java
MystemProbeResult probe = MystemProbe.probe(Path.of("/path/to/mystem"));
```

The probe runs a JSON request for `мама` and verifies that stdout looks like a MyStem JSON response.
