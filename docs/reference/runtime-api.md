# Runtime API Reference

Package: `io.github.ulviar.mystem4j`

Artifact: `io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0`

## Executable Resolution

When no explicit executable is configured, resolution uses:

1. `mystem4j.executable` system property;
2. `MYSTEM_PATH` environment variable;
3. `PATH`, if `searchPath(true)` is enabled.

`searchPath` defaults to `true`. Set `searchPath(false)` when the application must
use only an explicit path, system property, or environment variable.

`MystemExecutableNotFoundException` is thrown when no executable can be resolved or
the resolved path is not executable.

## Client Modes

| Mode | Builder call | Formats | Process model |
| --- | --- | --- | --- |
| One-shot | default `build()` | `JSON`, `XML`, `TEXT` | one process per request |
| Reusable session | `session()` | `JSON` only | one long-lived process |
| Pool | `pooled(...)` | `JSON` only | multiple long-lived processes |

Reusable session and pooled clients use one JSON response line as a protocol frame.
`analyze(String)` rejects text containing `\r` or `\n`; use one-shot mode or prepare
multiline input before calling these modes.

## Main Types

- `Mystem` - static entry point: `Mystem.builder()`.
- `MystemClientBuilder` - configures executable, options, mode, timeouts, size limits, and diagnostics.
- `MystemClient` - raw client with `analyze`, `analyzeFile`, `analyzeAll`, and `close`.
- `MystemOptions` - typed MyStem CLI options.
- `MystemPoolOptions` - pool size, warmup, idle, worker lifetime, and acquire timeout settings.
- `MystemProbe` - smoke-checks a MyStem executable.

## Client Methods

```java
MystemRawResult analyze(String text)
MystemFileContentResult analyzeFile(Path input)
MystemFileResult analyzeFile(Path input, Path output)
List<MystemRawResult> analyzeAll(Collection<String> texts)
void close()
```

## Results

- `MystemRawResult` - input text, raw output, format, request stats.
- `MystemFileContentResult` - input path, captured stdout, format, request stats.
- `MystemFileResult` - input path, output path, format, request stats.
- `MystemRequestStats` - elapsed time, execution mode, input/output sizes, and optional worker metadata.

## Options

Default options are JSON output, UTF-8 encoding, no grammar information, no
disambiguation, and all boolean MyStem flags disabled.

| `MystemOptions` field | Type | Default | CLI | Notes |
| --- | --- | --- | --- | --- |
| `newLineEachWord` | boolean | `false` | `-n` | print each word on a new line |
| `copyInput` | boolean | `false` | `-c` | copy full input to output |
| `dictionaryWordsOnly` | boolean | `false` | `-w` | print dictionary words only |
| `lemmaOnly` | boolean | `false` | `-l` | omit original word forms |
| `grammarInfo` | boolean | `false` | `-i` | print grammar information |
| `mergeWordForms` | boolean | `false` | `-g` | requires `grammarInfo` |
| `sentenceMarkers` | boolean | `false` | `-s` | requires `copyInput` |
| `encoding` | enum | `UTF_8` | `-e` | `CP866`, `CP1251`, `KOI8_R`, `UTF_8` |
| `disambiguate` | boolean | `false` | `-d` | contextual disambiguation |
| `englishGrammemes` | boolean | `false` | `--eng-gr` | English grammar tag names |
| `filterGrammar` | optional string | empty | `--filter-gram` | non-blank grammar filter |
| `fixlist` | optional path | empty | `--fixlist` | readable custom dictionary path |
| `format` | enum | `JSON` | `--format` | `JSON`, `XML`, or `TEXT` |
| `generateAll` | boolean | `false` | `--generate-all` | generate all hypotheses |
| `weight` | boolean | `false` | `--weight` | output lemma probability |

See the MyStem documentation for the linguistic meaning of CLI options:
<https://yandex.ru/dev/mystem/>.

## Limits And Timeouts

`MystemClientBuilder` exposes:

- `requestTimeout(Duration)`, default `3` seconds;
- `idleTimeout(Duration)`, default `Duration.ZERO` which disables idle timeout;
- `maxRequestChars(int)`, default `1_000_000`;
- `maxRequestBytes(int)`, default `4_000_000`;
- `maxResponseChars(int)`, default `8_000_000`;
- `maxResponseBytes(int)`, default `32_000_000`;
- `includeInputInDiagnostics(boolean)`, default `false`.

## Diagnostics

`includeInputInDiagnostics(false)` is the default so exception messages do not
include the full request text. `MystemProcessException.stderr()` exposes captured
stderr-like diagnostics when available.

## Exceptions

All runtime-specific exceptions extend `MystemException`.

- `MystemExecutableNotFoundException` - executable could not be found or is not executable.
- `MystemStartupException` - process/session/pool startup failed.
- `MystemRequestTimeoutException` - request timed out.
- `MystemProcessException` - MyStem exited unsuccessfully; exposes `exitCode()` and `stderr()`.
- `MystemProtocolException` - protocol, decoding, or runtime communication failure.
- `MystemOutputLimitException` - stdout/stderr/response exceeded configured limits.
- `MystemPoolExhaustedException` - no pooled worker was available before acquire timeout.
- `MystemClosedException` - request submitted after client close.
- `MystemInvalidOptionsException` - invalid runtime options or file paths.
