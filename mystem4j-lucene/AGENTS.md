# Lucene Agent Scope

## Scope

This module adapts runtime, model, and tokenization behavior to Lucene `Analyzer`
and `Tokenizer` contracts.

## Read

- [Package contract](src/main/java/io/github/ulviar/mystem4j/lucene/package-info.java)
- [Lucene reference](../docs/reference/lucene-api.md)
- [Lucene architecture](../docs/explanation/architecture.md#lucene-layer)
- Tests extending Lucene's `BaseTokenStreamTestCase` and `LuceneTestCase`

## Invariants

- Offsets are original UTF-16 coordinates and remain correct through `CharFilter`,
  chunking, truncation, supplementary characters, and MyStem omissions.
- Token stream lifecycle, final offsets, positions, types, keyword flags, reuse,
  and close ownership follow Lucene contracts.
- Analyzer and public Tokenizer apply the same JSON and client-profile policies.
- Chunk boundaries never split surrogate pairs. Document semantic changes when a
  long token is split by the configured chunk limit.
- Randomized tests must include a MyStem-like omission client, not only echo data.
- Public Lucene changes require index/query round trips where search semantics can
  change.

## Validation

```text
./gradlew :mystem4j-lucene:test
./gradlew :mystem4j-lucene:memorySmokeTest
```
