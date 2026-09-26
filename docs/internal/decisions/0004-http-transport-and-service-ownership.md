# ADR 0004: HTTP transport and service ownership

Status: accepted
Scope: HTTP client/server modules, native execution boundary and protocol

## Context

Applications that cannot launch native processes need the existing `MystemClient`
contract over a network. A shared service also needs explicit process ownership,
file transfer semantics and limits independent of native stdout limits.

## Decision

Add two independent production modules: HTTP client and HTTP server. Each exposes
runtime types and privately uses Jackson's streaming JSON codec. JDK HTTP APIs
provide the transport. No web framework, third shared protocol artifact or public
codec API is needed. The directional encoders/decoders are checked together by
wire contract tests. Version the protocol separately from artifact releases.

The server owns one native client after successful startup. Backend configuration
and process mode are fixed per service; HTTP connections have no native-session
identity. Closing a remote client only closes its own network resources. Upload
file bytes into private temporary files and return local caller paths. Never
accept executable options or filesystem paths from remote users.

JSON envelopes preserve Java strings, including isolated surrogates; native
encoding/preprocessing remains the owning runtime/model contract. Files transfer
as binary streams. Preserve native statistics and report transport limits and
errors separately. Bound HTTP bodies, admitted exchanges and full request time;
no application-level retries. Remote diagnostics omit native messages and stderr.

This extends ADR 0003's network capability rule specifically for the HTTP modules.
Native runtime, model, tokenization and Lucene still cannot perform network I/O.
Only the Gradle plugin downloads MyStem or accepts license opt-in. The server may
register a JVM shutdown hook; direct process creation remains in the runtime.

## Consequences

Native MyStem runs only on the service host. An Elasticsearch/Solr adapter is a
separate integration with its own versions and network permissions. HTTP file
operations are bounded transfers, unlike unrestricted native direct-file output.
Network cancellation cannot establish whether remote work completed. Public
hosting requires TLS/authentication and connection limits at deployment boundaries.

## Verification

Both artifacts participate in API baselines, JPMS consumers, POM-scope checks,
bytecode architecture checks, strict Javadoc and module tests. HTTP integration
covers strings/files/errors/lifecycle, and real-MyStem tests compare Lucene terms
and UTF-16 offsets with a local backend in addition to all three native modes.
