# Tokenization Agent Scope

## Scope

This module converts `MystemDocument` values into search tokens, forms, types, and
source ranges without depending on runtime process management or Lucene.

## Read

- [Package contract](src/main/java/io/github/ulviar/mystem4j/tokenization/package-info.java)
- [Tokenization reference](../docs/reference/tokenization-api.md)
- [Search architecture](../docs/explanation/architecture.md#search-tokenization)
- [Testing strategy](../docs/internal/testing-strategy.md#mystem4j-tokenization)

## Invariants

- Emitted tokens form a complete, ordered, non-overlapping partition when gap
  synthesis is enabled; each token text equals its original substring.
- Offset safety, gap recovery, and fallback forms are baseline behavior.
- Number, URL, email, and currency enrichment remains opt-in through named presets.
- URL/email merging must not consume unrelated text or surrounding punctuation.
- Suffix recovery must not expand an already present `+`, `++`, or `#` twice.
- Unicode-group tables are hypotheses backed by context tests, not standalone
  authority about MyStem behavior.

## Validation

```text
./gradlew :mystem4j-tokenization:test
./gradlew unicodeContextStressTest
```

Run the module `realMystemTest` when changing observed MyStem quirks.
