# API Stability

The APIs documented in `docs/reference` are the supported contract for MyStem4j
`0.1.0`, plus the explicitly marked HTTP additions under development for
`0.2.0-SNAPSHOT`. Existing release signatures are unchanged. This page defines
the public surface and compatibility policy.

All artifacts target Java 25 bytecode. Applications need Java 25 or newer; builds
applying the MyStem4j Gradle plugin must also run Gradle on Java 25 or newer.

## Public API

These packages are public:

- `io.github.ulviar.mystem4j`
- `io.github.ulviar.mystem4j.model`
- `io.github.ulviar.mystem4j.tokenization`
- `io.github.ulviar.mystem4j.lucene`
- `io.github.ulviar.mystem4j.kotlin`
- `io.github.ulviar.mystem4j.gradle`
- `io.github.ulviar.mystem4j.http` (development)
- `io.github.ulviar.mystem4j.server` (development)

The Maven artifacts, Gradle plugin id, JPMS module names, and documented public
types are part of the release surface.

Procwright and Jackson Core are implementation dependencies. They are supplied at
runtime, but are not exposed on a Gradle consumer's compile classpath or through
JPMS transitive readability. Applications that use Procwright or Jackson APIs
directly must declare their own dependencies on those libraries.

## Compatibility Policy Before 1.0

Patch releases should avoid breaking documented APIs unless a correctness issue
requires it. Minor `0.x` releases may make incompatible API changes; document
their effect on callers in the relevant API contract.

Behavior related to MyStem output, Unicode offset alignment, and Lucene offsets is
treated as compatibility-sensitive. A change that alters emitted offsets, token
positions, or default token forms should be documented even when Java signatures do
not change.

## Internal Details

Build tasks used for release validation, benchmark wiring, API-baseline files, and
maintainer checklists are not user-facing API. They can change without a public API
compatibility guarantee.
