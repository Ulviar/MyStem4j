# Runtime API Reference

Package: `io.github.ulviar.mystem4j`

Artifact: `io.github.ulviar.mystem4j:mystem4j-runtime:0.2.0`

## Executable Resolution

When no explicit executable is configured, resolution uses:

1. `mystem4j.executable` system property;
2. `MYSTEM_PATH` environment variable;
3. `PATH`, if `searchPath(true)` is enabled.

`searchPath` defaults to `true`. Set `searchPath(false)` when the application must
use only an explicit path, system property, or environment variable.

`MystemExecutableNotFoundException` is thrown when no executable can be resolved or
the resolved path is not executable.
PATH discovery skips directories and other non-regular files, continuing to the
next candidate. Explicit paths, the property and `MYSTEM_PATH` must name a regular
executable file; a symlink to one is accepted. Relative executable paths are resolved
against the JVM working directory when the client is built, independently of PATH lookup.

## Client Modes

| Mode | Builder call | Formats | Process model |
| --- | --- | --- | --- |
| One-shot | default `build()` | `JSON`, `XML`, `TEXT` | one process per request |
| Reusable session | `session()` | `JSON` only | one long-lived process |
| Pool | `pooled(...)` | `JSON` only | multiple long-lived processes |

Reusable session and pooled clients use one JSON response line as a protocol frame.
`analyze(String)` rejects text containing `\r` or `\n`; use one-shot mode or prepare
multiline input before calling these modes.

Reusable session and pooled clients also reject `newLineEachWord(true)`. MyStem
then writes one JSON line per word, which is incompatible with the one-line-per-request
protocol used by these modes.

These mode constraints and the readable, regular-file requirement for `fixlist`
are checked before resolving or starting the executable.

## Main Types

- `Mystem` - static entry point: `Mystem.builder()`.
- `MystemClientBuilder` - configures executable, options, mode, timeouts, size limits, and diagnostics.
- `MystemClient` - raw client with `analyze`, `analyzeFile`, `analyzeAll`, `executionProfile`,
  `outputFormat`, and `close`.
- `MystemOptions` - typed MyStem CLI options.
- `MystemPoolOptions` - pool size, warmup, idle, worker lifetime, and acquire timeout settings.
- `MystemProbe` - smoke-checks a MyStem executable.

## Client Methods

```java
MystemRawResult analyze(String text)
MystemFileContentResult analyzeFile(Path input)
MystemFileResult analyzeFile(Path input, Path output)
List<MystemRawResult> analyzeAll(Collection<String> texts)
MystemClientExecutionProfile executionProfile()
Optional<MystemOutputFormat> outputFormat()
void close()
```

`executionProfile()` describes the process model of built-in clients:
`ONE_SHOT_PROCESS_PER_REQUEST`, `REUSABLE_SESSION`, or `POOLED_SESSIONS`. Custom
client implementations may keep the default `UNKNOWN`.

`outputFormat()` returns the configured output format for built-in clients. Custom
client implementations may keep the default empty value.

## Results

- `MystemRawResult` - input text, raw output, format, request stats.
- `MystemFileContentResult` - input path, captured stdout, format, request stats.
- `MystemFileResult` - input path, output path, format, request stats.
- `MystemRequestStats` - elapsed time, execution mode, and input/output sizes.

### Request Statistics

Statistics describe successful requests; a failed request throws without returning
statistics. `elapsed` has these boundaries:

| Mode | Included | Excluded |
| --- | --- | --- |
| One-shot text/file | process startup, execution, output collection and cleanup | runtime argument validation and file-size inspection |
| Session | sending the request and receiving its response | waiting behind another caller on the same client; process startup |
| Pool | FIFO admission, worker acquisition (including creation if needed), and execution | client construction and initial warmup |

Character counts are Java UTF-16 code units. Text input bytes use the configured
encoding and exclude the protocol newline added by session/pool modes. Captured
output counts include line endings. One-shot output bytes count raw captured bytes;
session/pool output has a normalized final `\n`, and its byte count measures that
decoded response re-encoded with the configured encoding. These can differ from
original process bytes if line termination changed or decoding replaced malformed
input. Counts use `-1` when unknown, never to mean zero.

For file input, `inputChars` is `-1` and `inputBytes` is the file size inspected after
successful execution. Direct file output likewise has `outputChars == -1` and the
post-execution file size in `outputBytes`. File byte sizes become `-1` if unavailable;
concurrent external file changes can affect these measurements. Returned file paths
are the supplied paths, not necessarily absolute paths. Relative file paths use the
JVM working directory; filenames beginning with `-` are treated as files, not CLI options.

## Options

Default options are JSON output, UTF-8 encoding, no grammar information, no
disambiguation, and all boolean MyStem flags disabled.

| `MystemOptions` field | Type | Default | CLI | Notes |
| --- | --- | --- | --- | --- |
| `newLineEachWord` | boolean | `false` | `-n` | print each word on a new line; one-shot only |
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
| `fixlist` | optional path | empty | `--fixlist` | custom dictionary path checked when a client is built |
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

`requestTimeout` excludes a session caller's wait behind another caller and the
pool's admission/acquisition waits. For one-shot text and file requests, including
probes, it bounds waiting for the started process to exit; startup, output
collection, and cleanup can add time. It is not an end-to-end deadline for the
entire `analyze` call. The broader `elapsed` statistic is described above.

`idleTimeout` measures process I/O inactivity (stdin/stdout/stderr), including during
an active request. A silent request can lose its worker before `requestTimeout`
expires. A reusable client whose process has stopped must be closed and replaced;
a pool replaces failed workers. This timeout does not apply to one-shot requests,
including file requests.

Request limits apply to the payload passed to `analyze(String)`: UTF-16 code units
and bytes in the configured encoding. The protocol newline added by session and
pool modes is excluded. A payload exactly at either limit is accepted. Rejected
oversized input leaves the client usable for the next valid request.

Response limits apply to captured process output in every mode, including line
endings. `maxResponseChars` bounds decoded stdout; `maxResponseBytes` bounds
captured output and each stream's pending protocol buffer. Exceeding stdout limits
fails the request. One-shot requests also fail when captured stderr exceeds its
byte limit; session and pool text requests discard excess stderr and can still
return a valid stdout response. File inputs and output written directly to a file
are not bounded by these in-memory payload limits.

## Closing And Interruption

Built-in clients have idempotent `close()`. Closing waits for active requests to
finish or time out, releases their process resources, and rejects later requests
with `MystemClosedException`. Use a finite `requestTimeout` appropriate for the
largest request; `close()` is graceful shutdown, not immediate cancellation.

Interrupting a thread executing a request fails that request with `MystemException`
and preserves its interrupt flag. The affected child process is terminated. A
failed reusable session must be closed and replaced; a pool replaces a failed
worker. Oversized input rejected before execution does not have this effect.

## Pool Options

Create `MystemPoolOptions` with `MystemPoolOptions.builder()`. It controls pooled
JSON-line sessions:

| Option | Default | Meaning |
| --- | --- | --- |
| `maxSize` | available processors, capped at 256 | maximum number of live MyStem workers; range `1..256` |
| `warmupSize` | `0` | workers started when the pool is opened |
| `minIdle` | `0` | idle workers the pool tries to keep available |
| `acquireTimeout` | `2` seconds | maximum wait for admission; also bounds worker acquisition after admission |
| `hookTimeout` | `2` seconds | maximum time for one worker health-check or reset hook |
| `maxRequestsPerWorker` | `Integer.MAX_VALUE` | requests served by one worker before replacement |
| `maxWorkerAge` | `Duration.ZERO` | worker lifetime limit; zero disables age-based replacement |
| `backgroundReplenishment` | `true` | whether to maintain `minIdle` in the background; explicit warmup and on-demand startup still apply when disabled |

Use `maxSize` to match expected concurrent analysis work. Use `maxRequestsPerWorker`
or `maxWorkerAge` when the MyStem process should be periodically replaced during
long-running indexing jobs. Worker-age rotation does not interrupt an active request.
Calling `pooled()` after explicitly configuring pool options preserves those options;
on a fresh client builder it uses the defaults above.

Text requests enter the pool through a FIFO admission queue with `maxSize` slots.
Repeated callers cannot bypass callers already waiting for capacity. The admission
wait has an `acquireTimeout` deadline; if a worker must be replenished afterward,
its separate acquisition stage uses the same timeout. A request timeout begins
when the worker executes the request. Interruption while waiting for admission
preserves the interrupt flag and does not terminate another caller's worker.

## Diagnostics

`includeInputInDiagnostics(false)` is the default: the runtime does not append the
full request text to one-shot process-failure messages. This also controls file-path
diagnostics for file requests in every mode. It does not redact text printed by
MyStem to stderr. `MystemProcessException.stderr()` exposes bounded, possibly
truncated stderr-like diagnostics when available.

Reusable session and pooled clients drain process stdout and stderr through
bounded protocol buffers. Excess stderr is discarded without failing the request;
a bounded diagnostic tail is retained separately. This keeps diagnostic noise from
causing unbounded memory growth or rejecting a valid stdout response. If no stdout
response arrives, the configured request timeout still applies.

## Exceptions

All runtime-specific exceptions extend `MystemException`.

- `MystemExecutableNotFoundException` - executable could not be found or is not executable.
- `MystemStartupException` - process/session/pool startup failed.
- `MystemRequestTimeoutException` - request timed out.
- `MystemProcessException` - MyStem exited unsuccessfully; exposes `exitCode()` and `stderr()`.
- `MystemProtocolException` - protocol, decoding, or runtime communication failure.
- `MystemOutputLimitException` - stdout exceeded a response limit, or one-shot stderr capture exceeded its byte limit.
- `MystemPoolExhaustedException` - no pooled worker was available before acquire timeout.
- `MystemClosedException` - request submitted after client close.
- `MystemInvalidOptionsException` - invalid runtime options or file paths.
