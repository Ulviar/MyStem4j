# Kotlin Agent Scope

## Scope

This module is a thin Kotlin facade over runtime APIs. It must improve Kotlin call
sites without creating a second runtime model.

## Read

- [Package contract](src/main/java/io/github/ulviar/mystem4j/kotlin/package-info.java)
- [Kotlin reference](../docs/reference/kotlin-api.md)
- [Runtime reference](../docs/reference/runtime-api.md)
- Existing Kotlin tests and `api/mystem4j-kotlin.api`

## Invariants

- Kotlin helpers preserve Java defaults, validation, lifecycle, and exceptions.
- Nested DSL receivers cannot leak operations from an outer configuration scope.
- Public declarations use explicit visibility and stable JVM names where overloads
  require them.
- Do not update the BCV dump merely to make a failure green. Review the signature
  change first, then run `apiDump` intentionally.
- Keep this module runtime-focused unless a separate higher-level Kotlin artifact
  is deliberately designed.

## Validation

```text
./gradlew :mystem4j-kotlin:test :mystem4j-kotlin:apiCheck -Pmystem4j.useMavenLocal=true
```
