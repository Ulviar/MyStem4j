# Run MyStem over HTTP

Use a remote client when the application cannot start native processes or when
several applications should share one MyStem service. The client implements
`MystemClient`, so it also works with the existing Lucene analyzer and Kotlin
extensions. No Solr or Elasticsearch plugin is included; their server-specific
adapters and permissions are still needed.

The HTTP modules require MyStem4j `0.2.0` and Java 25 on both sides. The steps
below build the standalone service from this checkout; applications resolve the
client from Maven Central.

## Start a local service

Install MyStem 3.1 for the **server's** operating system and CPU, and accept its
[Yandex license](https://yandex.ru/legal/mystem/ru/). You can use the
[Gradle preparation plugin](prepare-mystem-with-gradle.md) to obtain it. The service
and Docker image do not download or bundle MyStem.

From the repository root, with `JAVA_HOME` pointing to JDK 25:

```bash
./gradlew :mystem4j-http-server:installDist

MYSTEM_EXECUTABLE=/absolute/path/to/mystem \
  mystem4j-http-server/build/install/mystem4j-http-server/bin/mystem4j-http-server
```

Replace `/absolute/path/to/mystem` with your installed executable. The service
listens on `127.0.0.1:8080` and uses a pool of four native text workers. From another
terminal, check the actual analysis path:

```bash
curl --fail-with-body http://127.0.0.1:8080/v1/analyze \
  -H 'Content-Type: application/json' \
  --data-binary '{"text":"Кошки спят."}'
```

The response is a JSON envelope whose `output` string contains raw MyStem JSON.
`GET /health/live` checks HTTP liveness only; it does not verify the native binary.
The service uses embedded Jetty Core over HTTP/1.1; no separate Jetty installation
is needed. Stop the foreground service with Ctrl-C. The shutdown hook closes its
native client.
The installed distribution directory can be copied to a machine with Java 25;
its `bin` launcher and `lib` directory must stay together. Windows uses the `.bat`
launcher and environment variables set through PowerShell or the system shell.

## Connect a Java application

In the consuming application's Gradle build:

```kotlin
plugins { java }
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-http-client:0.2.0")
}
```

```java
import io.github.ulviar.mystem4j.http.MystemHttpClient;
import java.net.URI;

public class RemoteAnalysis {
    public static void main(String[] args) {
        try (var client = MystemHttpClient.builder(URI.create("http://localhost:8080/")).build()) {
            var result = client.analyze("Кошки спят.");
            System.out.println(result.output());
            System.out.println(client.executionProfile());
        }
    }
}
```

The application needs no MyStem binary. Reuse one client across requests and
threads, and close it when the application stops. Client construction contacts
the server to learn its output format and execution profile. Closing the client
leaves the shared service running.

For Lucene, also declare `mystem4j-lucene:0.2.0` and pass this client to
`new MystemLuceneAnalyzer(client)` as shown in the [Lucene guide](use-lucene-analyzer.md).
Close the analyzer before the client. The standalone service uses JSON, copied
input, grammar information and disambiguation,
which match this scenario. Existing Lucene preprocessing still handles multiline
fields and maps tokens back to their original UTF-16 offsets.

## Select a process mode

Set `MYSTEM_MODE` in the server process environment before starting it. With
Docker Compose, edit `services.mystem.environment` in the Compose file; exported
host variables are not automatically passed into containers:

| Value | Text requests | File requests |
| --- | --- | --- |
| `oneshot` | A process per request; multiline text accepted | Separate one-shot process |
| `session` | One shared process; calls serialized; single-line text | Separate one-shot process, serialized by the session client |
| `pooled` (default) | Bounded worker pool; concurrent single-line text | Separate one-shot process outside the text pool |

HTTP connections are not native sessions. Two HTTP clients talking to one
session-mode service share that service's single native session. You do not need
a session identifier or an HTTP client pool to use pooled native processing.

For repeated or concurrent analysis, use `pooled`. In `session` mode, a native
execution/protocol failure can invalidate the session; restart that service.
A pool retires failed workers and can accept later requests without retrying the
failed request. A service instance has fixed native options; use separate
instances for different configurations.

## Analyze local files

Within the Java client's try-with-resources block:

```java
var captured = client.analyzeFile(java.nio.file.Path.of("input.txt"));
System.out.println(captured.output());

var saved = client.analyzeFile(
        java.nio.file.Path.of("input.txt"), java.nio.file.Path.of("output.json"));
System.out.println(saved.output());
```

`input.txt` exists on the **client** machine and contains UTF-8 for the standalone
service. Only its bytes are uploaded; the server never opens the caller's path.
`output.json` is downloaded to the client machine. Its parent directory must exist.
File requests accept multiline input in every mode. Uploads and downloads have
HTTP byte limits, even when the native file API itself has no text-size limit.
A failed transfer preserves an existing output file. Do not change those paths
while a request is running.

## Run in Docker

Build the JVM distribution first. Obtain a **Linux x64** MyStem binary separately;
a macOS binary will not work in a Linux container. The commands deliberately choose
`linux/amd64` to match the published Linux x64 MyStem distribution. On an ARM host,
Docker must provide x86-64 emulation.

```bash
./gradlew :mystem4j-http-server:installDist
docker build --platform linux/amd64 -f docker/Dockerfile \
  -t mystem4j-http:0.2.0 .

export MYSTEM_BINARY=/absolute/path/to/linux-x64/mystem
chmod a+rx "$MYSTEM_BINARY"
docker compose -f docker/compose.yaml up -d
curl --fail-with-body http://127.0.0.1:8080/v1/analyze \
  -H 'Content-Type: application/json' --data-binary '{"text":"Кошки спят."}'
```

The supplied Compose file mounts the binary read-only, runs as UID/GID 10001,
binds the host port to loopback, uses an init process for signal handling, and
provides a 256 MiB temporary filesystem. It contains no token or native binary.
Use `docker compose -f docker/compose.yaml down` to stop it. Adjust temporary
storage and request/concurrency limits together for large file workloads: native
file output is checked after the process writes it.

To connect another container on the same Compose network, use
`http://mystem:8080/`. To expose the service beyond that network, configure a TLS
reverse proxy and authentication instead of publishing an unauthenticated port
on every host interface. Edit `services.mystem.environment` and add a second volume
in `docker/compose.yaml`, for example:

```yaml
    environment:
      MYSTEM_MODE: session
      MYSTEM_TOKEN_FILE: /run/secrets/mystem-token
    volumes:
      - type: bind
        source: ${MYSTEM_BINARY:?Set MYSTEM_BINARY}
        target: /opt/mystem/mystem
        read_only: true
      - type: bind
        source: /absolute/path/to/mystem-token
        target: /run/secrets/mystem-token
        read_only: true
```

Keep the rest of the service definition. The token file must be readable by UID
10001 and contain one nonempty printable ASCII token without spaces. Apply changes
with `docker compose -f docker/compose.yaml up -d`. On the Java client, configure
`.bearerToken(System.getenv("MYSTEM_TOKEN"))`; set that environment variable to the
same token. Do not put the token in a URL, image layer or committed Compose file.

The JDK base image is selected by a moving Java 25 tag. Pin its digest in your
own deployment when reproducible image bytes are required. MyStem's separate
license still applies to any redistribution of its executable.

## Tune limits and timeouts

The launcher accepts environment variables; `--help` prints the same names:

| Variable | Default | Purpose |
| --- | --- | --- |
| `MYSTEM_EXECUTABLE` | required | Installed binary path |
| `MYSTEM_HOST`, `MYSTEM_PORT` | `127.0.0.1`, `8080` | Listening address; Docker image defaults host to `0.0.0.0` |
| `MYSTEM_MODE` | `pooled` | Process mode |
| `MYSTEM_POOL_SIZE` | `4` | Maximum native text workers |
| `MYSTEM_POOL_ACQUIRE_TIMEOUT_MS` | `2000` | Native pool admission/acquisition timeout |
| `MYSTEM_NATIVE_TIMEOUT_MS` | `30000` | Native execution timeout |
| `MYSTEM_HTTP_TIMEOUT_MS` | `45000` | Handler deadline, including transfers |
| `MYSTEM_HTTP_CONCURRENCY` | `16` | Admitted exchanges; includes file calls |
| `MYSTEM_HTTP_MAX_REQUEST_BYTES` | `8388608` | Upload/JSON limit; also sets native request limits |
| `MYSTEM_HTTP_MAX_RESPONSE_BYTES` | `67108864` | Download/JSON limit; also sets native capture limits |
| `MYSTEM_SHUTDOWN_GRACE_SECONDS` | `5` | Drain time before interrupting remaining handlers |
| `MYSTEM_TEMP_DIRECTORY` | JVM temp | Existing writable temporary-file parent |
| `MYSTEM_TOKEN_FILE` | absent | UTF-8 bearer token file, up to 4096 bytes; surrounding whitespace removed |

Jetty also bounds accepted connections and request headers. Its per-instance
limits and the distinction between idle timeout and total request deadline are
listed in the [server contract](../reference/http-api.md#server-contract).

Size the JVM heap for concurrent captured responses: the native output string
and its JSON encoding coexist in memory. File downloads stream, but native file
output still needs temporary disk space. Reduce body limits or concurrency for
small containers.

Leave time for the server's admission and native execution inside the HTTP
client deadline. JSON escape sequences count toward transport limits. With a
slow client or proxy, the HTTP deadline can expire even after MyStem has completed.
There is no automatic analysis retry; see the [HTTP reference](../reference/http-api.md)
for error codes, cancellation and file replacement guarantees.

## Embed the service

For custom MyStem options, construct a native client and pass it to the server
builder. Declare `io.github.ulviar.mystem4j:mystem4j-http-server:0.2.0` from
Maven Central. The following startup pattern makes ownership explicit:

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.server.MystemHttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;

var backend = Mystem.builder().executable(Path.of("/opt/mystem/mystem")).pooled().build();
MystemHttpServer server;
try {
    server = MystemHttpServer.builder(backend)
            .address(new InetSocketAddress("127.0.0.1", 8080)).start();
} catch (Exception startupFailure) {
    backend.close();
    throw startupFailure;
}
// Keep server alive while serving requests. The application shutdown hook calls server.close().
```

After successful startup, the server owns and closes the backend. The native
client's configured format, encoding, limits and process mode remain in force.
The HTTP service never accepts arbitrary executable paths or native options from
remote callers.

Jetty logs through SLF4J. Reuse your application's SLF4J 2.x provider when embedding;
the server dependency does not install one. If the application has no provider,
add Jetty's console logger to its Gradle dependencies:

```kotlin
runtimeOnly("org.eclipse.jetty:jetty-slf4j-impl:12.1.13")
```

Choose one provider; omit this dependency when the application already has one.
The standalone distribution includes this logger and needs no logging setup.
