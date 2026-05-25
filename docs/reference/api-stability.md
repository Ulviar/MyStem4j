# API Stability

MyStem4j `0.1.0` is the first public release. The APIs documented in `docs/reference`
are intended public APIs, but the project is still before `1.0`.

## Public API

These packages are public:

- `io.github.ulviar.mystem4j`
- `io.github.ulviar.mystem4j.model`
- `io.github.ulviar.mystem4j.tokenization`
- `io.github.ulviar.mystem4j.lucene`
- `io.github.ulviar.mystem4j.kotlin`
- `io.github.ulviar.mystem4j.gradle`

The Maven artifacts, Gradle plugin id, JPMS module names, and documented public
types are part of the release surface.

## Compatibility Policy Before 1.0

Patch releases should avoid breaking documented APIs unless a correctness issue
requires it. Minor `0.x` releases may make incompatible API changes, but those
changes should be called out in the changelog.

Behavior related to MyStem output, Unicode offset alignment, and Lucene offsets is
treated as compatibility-sensitive. A change that alters emitted offsets, token
positions, or default token forms should be documented even when Java signatures do
not change.

## Internal Details

Build tasks used for release validation, benchmark wiring, API-baseline files, and
maintainer checklists are not user-facing API. They can change without a public API
compatibility guarantee.
