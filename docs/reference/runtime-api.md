# Runtime API Reference

Package: `io.github.ulviar.mystem4j`

Artifact: `mystem4j-runtime`

## Executable Resolution

When no explicit executable is configured, resolution uses:

1. `mystem4j.executable` system property;
2. `MYSTEM_PATH` environment variable;
3. `PATH`, if `searchPath(true)` is enabled.

`MystemExecutableNotFoundException` is thrown when no executable can be resolved.

## Client Modes

| Mode | Builder call | Formats | Process model |
| --- | --- | --- | --- |
| One-shot | default `build()` | `JSON`, `XML`, `TEXT` | one process per request |
| Reusable session | `session()` | `JSON` only | one long-lived process |
| Pool | `pooled(...)` | `JSON` only | multiple long-lived processes |

Reusable session and pooled clients use one JSON response line as a protocol frame. `analyze(String)` rejects text containing `\r` or `\n`; use one-shot mode or split/prepare multiline input before calling these modes.

## Main Types

- `Mystem` - static entry point: `Mystem.builder()`.
- `MystemClientBuilder` - configures executable, options, mode, timeouts, size limits, and diagnostics.
- `MystemClient` - raw client with `analyze`, `analyzeFile`, `analyzeAll`, and `close`.
- `MystemOptions` - typed MyStem CLI options.
- `MystemPoolOptions` - pool size, warmup, idle, worker lifetime, and acquire timeout settings.
- `MystemProbe` - smoke-checks a MyStem executable.

## Results

- `MystemRawResult` - input text, raw output, format, request stats.
- `MystemFileContentResult` - input path, captured stdout, format, request stats.
- `MystemFileResult` - input path, output path, format, request stats.
- `MystemRequestStats` - elapsed time, execution mode, input/output sizes, and optional worker metadata.

## Options

| `MystemOptions` field | CLI | Notes |
| --- | --- | --- |
| `newLineEachWord` | `-n` | print each word on a new line |
| `copyInput` | `-c` | copy full input to output |
| `dictionaryWordsOnly` | `-w` | print dictionary words only |
| `lemmaOnly` | `-l` | omit original word forms |
| `grammarInfo` | `-i` | print grammar information |
| `mergeWordForms` | `-g` | requires `grammarInfo` |
| `sentenceMarkers` | `-s` | requires `copyInput` |
| `encoding` | `-e` | `CP866`, `CP1251`, `KOI8_R`, `UTF_8` |
| `disambiguate` | `-d` | contextual disambiguation |
| `englishGrammemes` | `--eng-gr` | English grammeme names |
| `filterGrammar` | `--filter-gram` | non-blank grammar filter |
| `fixlist` | `--fixlist` | readable custom dictionary path |
| `format` | `--format` | defaults to `JSON` |
| `generateAll` | `--generate-all` | generate all hypotheses |
| `weight` | `--weight` | output lemma probability |

## Limits and Timeouts

`MystemClientBuilder` exposes:

- `requestTimeout(Duration)`;
- `idleTimeout(Duration)`;
- `maxRequestChars(int)`;
- `maxRequestBytes(int)`;
- `maxResponseChars(int)`;
- `maxResponseBytes(int)`;
- `includeInputInDiagnostics(boolean)`.

`includeInputInDiagnostics(false)` is the default. Session and pool protocol diagnostics never include stdout transcript text in exception messages; `MystemProcessException.stderr()` is populated only from stderr transcript lines.

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
