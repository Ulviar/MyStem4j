# Model Agent Scope

## Scope

This module owns MyStem JSON parsing, immutable morphology models, grammar parsing,
Unicode preparation, text issues, and offset alignment.

## Read

- [Package contract](src/main/java/io/github/ulviar/mystem4j/model/package-info.java)
- [Model reference](../docs/reference/model-api.md)
- [Unicode architecture](../docs/explanation/architecture.md#unicode-and-offsets)
- [Testing strategy](../docs/internal/testing-strategy.md#mystem4j-model)

## Invariants

- Public offsets are half-open Java UTF-16 ranges in the caller's original text.
- Prepared-text mappings are total, monotonic, and composed back to original input.
- Alignment is left-to-right; the nearest compatible fuzzy occurrence must beat a
  later exact duplicate.
- Parser output order and immutable collections are preserved.
- This module does not launch MyStem and does not depend on Lucene.
- Every new dropped-character rule needs a real-MyStem observation, contextual
  regression cases, and Lucene-facing verification where offsets are affected.

## Validation

```text
./gradlew :mystem4j-model:test -Pmystem4j.useMavenLocal=true
./gradlew realMystemUnicodeStress -Pmystem4j.useMavenLocal=true -Dmystem4j.executable=/path/to/mystem
```
