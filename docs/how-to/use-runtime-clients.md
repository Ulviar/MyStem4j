# Use runtime clients

`mystem4j-runtime` starts MyStem processes and returns raw MyStem output. It does
not parse JSON into model objects. For tokens, lemmas, grammar, or offsets, use
[Parse MyStem output](parse-mystem-output.md).

## Add the module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
}
```

## One-shot text requests

One-shot clients start a fresh MyStem process per request and support `JSON`,
`XML`, and `TEXT`.

Install or prepare MyStem separately, then pass its executable path with
`.executable(Path.of(...))`. If `.executable(...)` is omitted, the runtime resolves
MyStem from `mystem4j.executable`, then `MYSTEM_PATH`, then `PATH`.

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.nio.file.Path;

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

## Reusable JSON-line session

Reusable mode keeps one MyStem process open. Use it for many small sequential
requests from one caller.

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
`\n`. Use one-shot mode for raw multiline input. If a multiline request must go
through a JSON-line client and you need offsets in the original text, use the
preprocessing flow from [Parse MyStem output](parse-mystem-output.md); that flow
requires `mystem4j-model`.

## Pooled sessions

Pooled mode keeps several JSON-line MyStem processes open for concurrent callers,
for example indexing services.

```java
import java.time.Duration;

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

One-shot and pooled clients can be shared until `close()` is called. Reusable
session clients serialize requests through one MyStem process; use them from one
caller at a time.

## File requests

File requests avoid loading large outputs into JVM memory when MyStem can write
the result directly.

```java
import io.github.ulviar.mystem4j.MystemFileContentResult;
import io.github.ulviar.mystem4j.MystemFileResult;

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .build()) {
    MystemFileContentResult stdout = client.analyzeFile(Path.of("input.txt"));
    MystemFileResult file = client.analyzeFile(Path.of("input.txt"), Path.of("output.json"));
    System.out.println(stdout.output());
    System.out.println(file.output());
}
```

`analyzeFile(input)` captures stdout as a Java string. `analyzeFile(input, output)`
uses MyStem's native `mystem [options] input output` mode and writes directly to the
output path without keeping the full output in JVM memory. Input and output must be
different paths.

## Probe an executable

```java
import io.github.ulviar.mystem4j.MystemProbe;
import io.github.ulviar.mystem4j.MystemProbeResult;

MystemProbeResult probe = MystemProbe.probe(Path.of("/path/to/mystem"));
System.out.println(probe.output());
```

The probe starts MyStem, sends one JSON request for `мама`, and expects a clean
exit plus JSON-array output. Startup, timeout, process, and protocol failures are
reported as `MystemException` subclasses.
