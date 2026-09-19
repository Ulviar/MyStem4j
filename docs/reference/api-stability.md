# API Stability

MyStem4j `0.1.0` is the current development version of this independent, unreleased
project. The APIs documented in `docs/reference` are the supported contract for
local consumers; publication is not required to use the compatibility checks.

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

The Maven artifacts, Gradle plugin id, JPMS module names, and documented public
types are part of the release surface.

iCLI and Jackson Core are implementation dependencies. They are supplied at
runtime, but are not exposed on a Gradle consumer's compile classpath or through
JPMS transitive readability. Applications that previously compiled against these
libraries indirectly must declare them directly. MyStem4j public signatures are
unchanged by this dependency-scope correction.

## Compatibility Policy Before 1.0

Patch releases should avoid breaking documented APIs unless a correctness issue
requires it. Minor `0.x` releases may make incompatible API changes, but those
changes should be called out in the changelog.

Behavior related to MyStem output, Unicode offset alignment, and Lucene offsets is
treated as compatibility-sensitive. A change that alters emitted offsets, token
positions, or default token forms should be documented even when Java signatures do
not change.

`MystemSearchTermNormalizer.normalize(String)` is a public tokenization utility
shared with Lucene single-term normalization. Token forms retain their existing
literal values and add unique normalized aliases when needed; aliases have the
same source offsets and Lucene position. Normalization does not call MyStem.

The JSON parser now rejects wrong types, including explicit `null`, for known
fields with `MystemJsonParseException`. Missing fields retain their documented
defaults and unknown metadata is still ignored. Parser signatures are unchanged.

Offset correctness fixes make Lucene output independent of `Reader` fragmentation
and apply chunk limits to truncated prefixes. Fields affected by the old splitting
or truncation behavior should be reindexed. Hand-built model tokens with ranges
inside a surrogate pair, empty ranges for nonempty text, or overlapping/out-of-order
ranges now fail with
`MystemTokenizationException` instead of producing overlapping slices or an
incidental exception. Public signatures are unchanged.

Runtime limit corrections accept exact-size request payloads in session/pool modes
without counting the added protocol newline. Oversized input no longer invalidates
a reusable session. One-shot clients now enforce `maxResponseChars` on captured
stdout and wait for active requests in `close()`, matching the other built-in modes.
Invalid mode options and non-file fixlists fail before executable resolution.
These are behavioral corrections with unchanged public signatures.

Pooled text requests now use FIFO admission to prevent repeated callers starving
queued callers under sustained load. Admission and underlying worker acquisition
each use `acquireTimeout`; request execution keeps its own `requestTimeout`.

## Internal Details

Build tasks used for release validation, benchmark wiring, API-baseline files, and
maintainer checklists are not user-facing API. They can change without a public API
compatibility guarantee.
