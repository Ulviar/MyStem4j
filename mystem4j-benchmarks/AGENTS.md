# Benchmark Agent Scope

## Scope

This module separates Java-side parser, preprocessing, tokenization and Lucene
adapter costs from opt-in native request, Lucene analysis and indexing measurements.

## Read

- [Testing strategy](../docs/internal/testing-strategy.md#mystem4j-benchmarks)
- `MystemCoreBenchmark` and its correctness test
- [Performance measurement guide](../docs/how-to/measure-performance.md)
- Relevant production path before changing benchmark fixtures

## Invariants

- Benchmark setup must produce valid model, tokenization, and Lucene results.
- A smoke run proves wiring, not statistically meaningful performance.
- Do not present fake-client throughput as native MyStem latency or pool capacity.
- Keep correctness assertions outside timed benchmark methods.
- New performance claims need representative input sizes, warmup, forks, and a
  stated environment.

## Validation

```text
./gradlew :mystem4j-benchmarks:test :mystem4j-benchmarks:jmhCompileCheck -Pmystem4j.useMavenLocal=true
./gradlew :mystem4j-benchmarks:jmhSmoke -Pmystem4j.useMavenLocal=true
```

Run `:mystem4j-benchmarks:nativeJmh` with an explicit executable for native
performance claims. Default checks must remain independent of the native binary.
