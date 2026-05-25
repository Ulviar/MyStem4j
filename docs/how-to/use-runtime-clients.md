# Use Runtime Clients

`mystem4j-runtime` starts MyStem processes and returns raw MyStem output. It does
not parse JSON into model objects; use [Parse MyStem output](parse-mystem-output.md)
when you need tokens, lemmas, grammar, or offsets as Java objects.

## Add The Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
}
```

## One-Shot Text Requests

One-shot mode starts a new MyStem process for each request. It supports `JSON`,
`XML`, and `TEXT` output formats.

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemProbe;
import io.github.ulviar.mystem4j.MystemProbeResult;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.nio.file.Path;
import java.time.Duration;

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
    System.out.println(result.output());
}
```

`MystemOptions.builder().build()` defaults to JSON and UTF-8 with all boolean
MyStem flags disabled.

## Reusable JSON-Line Session

Reusable mode keeps one MyStem process open. It is useful when one thread sends
many small requests.

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .build())
        .session()
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
    System.out.println(result.output());
}
```

Reusable mode supports JSON only. It uses one input line as one request and one
output line as one response, so `analyze(String)` rejects text containing `\r` or
`\n`. Use one-shot mode for multiline input, or call
`MystemTextPreprocessor.prepareJsonLine` before sending text that must go through a
JSON-line client.

## Pooled Sessions

Pooled mode keeps several JSON-line MyStem processes open and is intended for
concurrent callers such as indexing services.

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .build())
        .pooled(pool -> pool
                .maxSize(4)
                .warmupSize(1)
                .minIdle(1)
                .acquireTimeout(Duration.ofSeconds(2)))
        .build()) {
    MystemRawResult result = client.analyze("Мама мыла раму.");
    System.out.println(result.output());
}
```

Options are fixed for the whole pool. Like reusable sessions, pooled clients accept
JSON only and reject raw multiline text.

## File Requests

Use file requests when the input or output is large enough that you do not want to
keep all output in JVM memory.

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .build()) {
    MystemFileContentResult stdout = client.analyzeFile(Path.of("input.txt"));
    MystemFileResult file = client.analyzeFile(Path.of("input.txt"), Path.of("output.json"));
    System.out.println(stdout.output());
    System.out.println(file.output());
}
```

`analyzeFile(input, output)` uses MyStem's native `mystem [options] input output`
mode and writes directly to the output path. Input and output must be different
paths.

## Probe An Executable

```java
MystemProbeResult probe = MystemProbe.probe(Path.of("/path/to/mystem"));
System.out.println(probe.output());
```

The probe runs one JSON request for `мама`. It succeeds when MyStem starts, exits
cleanly, and returns output that looks like a MyStem JSON array. Startup, timeout,
process, and protocol failures are reported as `MystemException` subclasses.
