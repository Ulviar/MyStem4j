# HTTP client and server

The HTTP modules are new in `0.2.0-SNAPSHOT` and are not part of the published
`0.1.0` release. See [Run MyStem over HTTP](../how-to/http-service.md) for local
installation, Docker and complete client examples. Both modules require Java 25.

| Artifact | JPMS module / public package | Public entry points |
| --- | --- | --- |
| `mystem4j-http-client` | `io.github.ulviar.mystem4j.http` | `MystemHttpClient.builder(URI)` |
| `mystem4j-http-server` | `io.github.ulviar.mystem4j.server` | `MystemHttpServer.builder(MystemClient)`, `MystemServerMain` |

Both expose the runtime API and keep Jackson Core as an implementation dependency.
The client uses `java.net.http`; the server uses `jdk.httpserver`. Neither parses
morphology, downloads MyStem, or depends on Lucene. The HTTP client never launches
a local native process, although the runtime API dependency brings Procwright
transitively.

## Client contract

`MystemHttpClient` implements every `MystemClient` method. Its builder performs a
metadata request when `build()` is called, so connection/authentication failure can
occur before analysis. The root URI can contain a reverse-proxy prefix; it must
not contain credentials, a query or a fragment. Redirects are not followed.

| Builder method | Default | Meaning |
| --- | --- | --- |
| `connectTimeout(Duration)` | 5 seconds | Establishing a new connection |
| `requestTimeout(Duration)` | 60 seconds | Upload, remote wait/execution and full response body |
| `maxRequestBytes(int)` | 8 MiB | JSON request or raw file upload |
| `maxResponseBytes(int)` | 64 MiB | JSON response or raw file download |
| `bearerToken(String)` | absent | Authorization for all requests, including metadata |

Durations must be positive and at most one day; byte limits must be positive.
Tokens must be nonempty printable ASCII without spaces. Treat a client configured
with a token as a secret-bearing object; use HTTPS outside a trusted network.

Instances are thread-safe. `analyzeAll` remains sequential and stops at the first
failure. `executionProfile()` describes the remote backend; `outputFormat()` is
cached during construction. Metadata methods work after close. A successful
response whose format differs from the cached value is rejected. Use consistent
backend configuration across replicas behind one endpoint.

`close()` cancels in-flight HTTP requests and shuts down this client's connections.
It does not stop the service. A request racing with close may succeed or fail;
requests submitted after close fail with `MystemClosedException`. Unlike native
clients, HTTP close does not drain remote work. Network failure, interruption or
an HTTP deadline does not prove that the server stopped processing. The library
has no application-level retry loop. Interrupted callers retain their interrupt
flag.

### Text, files and statistics

Text and captured output cross the wire as JSON strings, including escaped
isolated UTF-16 surrogates. No Unicode normalization, line-ending conversion or
morphology parsing occurs in the transport. Native encoding behavior remains as
specified by the [runtime](runtime-api.md); use the model preprocessor or Lucene
analyzer for input that MyStem itself cannot faithfully represent.

`analyzeFile(input)` uploads bytes and returns decoded native output.
`analyzeFile(input, output)` uploads and downloads bytes with bounded memory.
Input must be a readable regular file; output must be a different file, including
through hard links or symlinks, in an existing directory. Both returned paths are
the original caller-supplied local paths. Input bytes must use the server's native
encoding; the standalone service uses UTF-8.

Downloads use a temporary file in the destination directory. The destination is
replaced only after the full response and metadata validate. This requires atomic
file replacement support from that filesystem. Failure leaves an existing
destination unchanged, and temporary files are removed. As with local file APIs,
the caller must not modify or replace input/output paths during a request.

Statistics preserve backend values, including `-1` for unknown counts and
`ONE_SHOT_FILE` for native file calls. They exclude HTTP transfer time and do not
measure JSON body sizes. UTF-16 character counts retain their native meaning.

## Server contract

`MystemHttpServer.builder(backend).start()` takes ownership of the backend only
on success. On startup failure the caller closes the backend. A builder can
successfully start at most one service. Do not share the same backend between
multiple owning services. The backend must support concurrent calls and expose
its output format. All built-in native modes satisfy these requirements.

| Builder method | Default | Meaning |
| --- | --- | --- |
| `address(InetSocketAddress)` | `127.0.0.1:8080` | Resolved bind address; port 0 allocates a port |
| `maxConcurrentRequests(int)` | 16 | Admitted HTTP exchanges, including file transfers |
| `maxRequestBytes(int)` | 8 MiB | Upload / JSON body, checked even with chunked transfer |
| `maxResponseBytes(int)` | 64 MiB | Download / JSON body |
| `requestTimeout(Duration)` | 45 seconds | Handler deadline, including reads and writes |
| `shutdownGraceSeconds(int)` | 5 | Drain time before interrupting remaining handlers |
| `temporaryDirectory(Path)` | JVM temp directory | Existing writable parent for private request directories |
| `bearerToken(String)` | absent | Required on versioned endpoints |

Excess admitted work receives HTTP 429 immediately. This bounds backend calls and
body storage; it is not a TCP connection limit. The standalone launcher also sets
JDK HTTP server defaults of 256 connections and 60-second request/response socket
limits. Embedders own those JVM-wide settings or configure them at their proxy.

The handler deadline closes the exchange and interrupts the worker. Native clients
terminate interrupted execution according to the runtime contract; custom backends
must cooperate with interruption. The client may observe a transport failure when
the exchange closes rather than a complete 504 response. Explicit native timeouts
that finish within the HTTP deadline return 504.

Each file call creates a private temporary directory, invokes the native file API,
then removes its files. Native file calls still use a separate one-shot process in
all modes. The HTTP concurrency limit includes these processes; native pool size
alone does not limit them. Direct native file output is checked for size after
execution, so deploy with a disk quota or bounded temporary filesystem when disk
usage must be capped during execution.

Server `close()` stops admission, drains for the grace period, interrupts remaining
handlers, waits for their cleanup and closes the backend. Repeated close is safe.
The drain period is not a hard upper bound on arbitrary custom-backend cleanup.
A failed reusable native session remains failed; restart a session-mode service
after such a failure. Pooled mode replaces failed workers through the native pool.

## Wire protocol v1

Endpoints are relative to the service root. Queries, unknown paths and path suffixes
are rejected. Clients must use the exact media types below; content encoding such
as gzip is not supported. Strings in JSON use UTF-8 or JSON Unicode escapes.

| Method / path | Request body | Successful response |
| --- | --- | --- |
| `GET /health/live` | none | 204; HTTP liveness only, no authentication or native probe |
| `GET /v1/info` | none | 204 with version, format and profile headers |
| `POST /v1/analyze` | `application/json`: `{"text":"Кошки"}` | 200 `application/json`: `{"output":"raw MyStem output"}` |
| `POST /v1/files/content` | `application/octet-stream`: input bytes | Same JSON response as text analysis |
| `POST /v1/files/output` | `application/octet-stream`: input bytes | 200 `application/octet-stream`: output bytes |

JSON envelopes contain exactly the specified string field; extra/duplicate fields
and trailing data are rejected. JSON escaping counts against body limits, so a
Java string's length is not its HTTP byte length. Files do not contain JSON envelopes.

Versioned successful responses carry `X-Mystem-Version: 1` and
`X-Mystem-Format: JSON|XML|TEXT`. Metadata adds `X-Mystem-Profile` containing a
`MystemClientExecutionProfile` enum name. Analysis responses add:

- `X-Mystem-Elapsed`: ISO-8601 duration from `MystemRequestStats.elapsed()`;
- `X-Mystem-Mode`: `MystemExecutionMode` enum name;
- `X-Mystem-Input-Chars`, `X-Mystem-Input-Bytes`, `X-Mystem-Output-Chars`,
  `X-Mystem-Output-Bytes`: decimal counts, or `-1` if unknown.

Failures have no response body. `X-Mystem-Error` supplies a stable code. Process
failure can include `X-Mystem-Exit-Code`; raw stderr, exception messages, input and
server paths are omitted. The Java client maps codes to exceptions:

| HTTP status / code | Exception |
| --- | --- |
| 400 or 413 / `INVALID_REQUEST` | `MystemInvalidOptionsException` |
| 413 / `OUTPUT_LIMIT` | `MystemOutputLimitException` |
| 429 / `BUSY` | `MystemPoolExhaustedException` |
| 503 / `CLOSED` | `MystemClosedException` |
| 504 / `TIMEOUT` | `MystemRequestTimeoutException` |
| 502 / `STARTUP`, `PROTOCOL`, `PROCESS` | Corresponding runtime exception; process stderr is empty |
| 401 / `UNAUTHORIZED`; 404 / `NOT_FOUND`; 405 / `METHOD_NOT_ALLOWED`; 415 / `UNSUPPORTED_MEDIA_TYPE`; 500 / `INTERNAL` | `MystemException` |

A local HTTP response-size overflow is `MystemOutputLimitException`. Broken framing,
invalid successful responses and transport errors are `MystemProtocolException`.
There are no remote session handles, arbitrary command options, server-side path
arguments or HTTP shutdown endpoint.
