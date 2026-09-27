# ADR 0005: Embedded Jetty Core server

Status: accepted
Scope: HTTP server transport, lifecycle and distribution

## Context

[ADR 0004](0004-http-transport-and-service-ownership.md) initially used the public
JDK HTTP server API. A shared native-processing service needs connection controls,
HTTP parsing and a managed network lifecycle. Jetty Core provides these without
a Servlet container or an application framework. This decision replaces only
ADR 0004's server transport choice.

## Decision

Use Jetty 12.1 Core with a single HTTP/1.1 connector. Keep protocol v1 and the
public builders unchanged. The client continues to use `java.net.http`; neither
the runtime nor consumer-facing API acquires Jetty dependencies.

A nonblocking Core handler admits or rejects requests immediately. Admitted work
runs on an owned virtual-thread executor, including blocking body transfer and
native execution. Its absolute deadline interrupts the worker and closes the
HTTP/1.1 connection. Jetty failure notifications also interrupt active work.
Keep explicit body limits and native error mapping in the service: container
error pages must not reveal request data or backend diagnostics.

Use Jetty's graceful handler to drain requests, then close connections and wait
for interrupted workers to clean their temporary files before closing the backend.
A grace-period timeout is an expected shutdown path, not a failed close.

Configure connection acceptance, idle timeout and header size per instance.
TLS, HTTP/2 termination and a total header-read timeout remain proxy responsibilities.
These bounds and their values are owned by the [HTTP reference](../../reference/http-api.md).

Publish Jetty as an implementation dependency. Add its SLF4J provider only to the
standalone distribution and tests; an embedding application chooses its provider.

## Consequences

The server adds Jetty Core and SLF4J API dependencies, but no Servlet API. The
standalone launcher no longer mutates JVM-wide JDK HTTP server properties.
No throughput improvement is claimed without workload-specific measurements;
native processing may dominate end-to-end latency.

## Verification

Existing HTTP contract and real-native tests must pass unchanged. Transport tests
cover malformed framing, header limits, slow transfers, deadline cancellation and
graceful/forced shutdown. JPMS, bytecode dependency and POM-scope checks prevent
Jetty from leaking into the public API. The installed distribution is exercised
with all three native modes, file transfers and process shutdown.
