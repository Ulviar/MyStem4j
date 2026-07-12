# Benchmark Agent Scope

## Scope

This module measures Java-side parser, preprocessing, tokenization, and Lucene
adapter costs. It does not currently benchmark native MyStem process throughput.

## Read

- [Testing strategy](../docs/internal/testing-strategy.md#mystem4j-benchmarks)
- `MystemCoreBenchmark` and its correctness test
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
