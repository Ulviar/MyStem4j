# Runtime Agent Scope

## Scope

This module owns executable resolution, typed MyStem CLI options, process modes,
file requests, limits, diagnostics, and runtime exceptions.

## Read

- [Package contract](src/main/java/io/github/ulviar/mystem4j/package-info.java)
- [Runtime reference](../docs/reference/runtime-api.md)
- [Process architecture](../docs/explanation/architecture.md#process-modes)
- The closest tests under `src/test/java/io/github/ulviar/mystem4j`

## Invariants

- Runtime returns raw output and does not parse morphology or depend on Lucene.
- Runtime never downloads MyStem or accepts its license for the caller.
- One-shot supports process-bounded formats; reusable and pooled clients require
  one-line JSON framing.
- Character and byte limits, timeouts, stderr backlog, close behavior, and stable
  exception mapping are part of the contract.
- Fake executable tests must remain cross-platform Java launchers.
- Process lifecycle changes require resource-release and concurrent pool coverage.

## Validation

```text
./gradlew :mystem4j-runtime:test
./gradlew :mystem4j-runtime:memorySmokeTest
```

Run root `realMystemTest` for protocol assumptions involving the native binary.
Run root `realMystemPoolSoak` when pool lifecycle, concurrency, or performance can
change.
