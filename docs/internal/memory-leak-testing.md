# Memory Leak Testing Notes

Memory checks should focus on long-lived objects rather than one-time allocations.

## What To Check

- Lucene analyzers can cache tokenizer components. Tokenizers must release large field buffers and last emissions on `close()`.
- Runtime clients must release processes, sessions, executor resources, and stream readers on `close()`.
- Pooled clients must not retain borrowed request data after a request is returned to the pool.
- Gradle tasks should not keep open file handles to downloaded archives or extracted executables.

## Current Coverage

- Unit coverage checks that `MystemLuceneTokenizer` releases buffered field data on `close()`.
- `mystem4j-lucene:memorySmokeTest` repeats large-field analysis under a small heap.
- `mystem4j-tokenization:memorySmokeTest` repeats large-token classification under a small heap.
- `mystem4j-runtime:memorySmokeTest` checks that reusable, pooled, and timed-out workers release OS processes.
- Lucene randomized token stream tests exercise lifecycle behavior over many generated inputs.
- Runtime tests cover close behavior and process failure paths.

Run the focused memory smoke gate with:

```bash
./gradlew memorySmokeTest
```

## Useful Additional Gates

- A long-running analyzer loop under a small heap, for example repeated large-field analysis with `-Xmx128m`.
- A real MyStem pool loop that verifies child process count returns to baseline after closing clients.
- A profiler run with Java Flight Recorder or async-profiler around indexing-like workloads.
- A heap histogram comparison before and after repeated analyzer/client close cycles.
