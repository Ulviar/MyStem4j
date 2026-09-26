# ADR 0003: Executable module boundaries

Status: accepted; network capability extended by [ADR 0004](0004-http-transport-and-service-ownership.md)
Scope: production dependency graph, consumer compilation, and architecture gates

## Context

The module split separated process management, parsing, tokenization, integration,
and executable preparation. However, runtime and model exposed iCLI and Jackson
through Gradle `api` dependencies even though their public signatures did not use
those libraries and their JPMS descriptors correctly used non-transitive requires.
The combined JPMS consumer also had every library available, hiding accidental
coupling between otherwise independent entry points.

## Decision

Use `api` for dependencies represented in public signatures and `implementation`
for private mechanisms. Preserve the existing production modules and public APIs.
Lucene composes runtime, model, and tokenization directly; a shared utility or new
facade module would not improve this ownership boundary.

Keep the allowed production graph in `ModuleBoundaryPolicy`, independently of
actual dependency declarations. Validate compiled class references, public
signatures, JPMS descriptors, and the Gradle variants a consumer resolves.
Process creation belongs to runtime and the Gradle plugin; network access belongs
to the plugin. Parsing a URI and converting an international host name with
`IDN.toASCII` are value operations and remain available to tokenization; they do
not resolve hosts or access the network.

Compile and execute one consumer for each library with only that library's
dependency closure, on classpath and module path. Test boundary enforcement with
compiled invalid fixtures, rather than trusting that a green repository exercises
the rejection paths.

## Consequences

Applications using Procwright (formerly iCLI) or Jackson APIs must declare those dependencies directly.
Both libraries remain available at runtime. Java/Kotlin signatures and text
behavior are unchanged.

An intentional new production dependency requires reviewing the boundary policy.
Test-only dependencies do not enlarge production permissions. Bytecode rules do
not inspect reflective behavior or third-party implementations; existing execution
tests remain necessary.

## Verification

`architectureCheck` is part of root `check`. It includes the independent consumer
checks and publication metadata checks. `buildSrc` tests exercise forbidden
dependencies and prevent POM scope checks from matching a neighboring dependency.
