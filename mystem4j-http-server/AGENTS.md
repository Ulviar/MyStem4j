# HTTP Server Agent Scope

## Scope

Owns HTTP admission, temporary files, backend ownership and standalone deployment.

## Read

- [HTTP contract](../docs/reference/http-api.md)
- [HTTP guide](../docs/how-to/http-service.md)
- Current tests in this module and the HTTP server contract suite

## Invariants

- Preserve Java strings without normalization, including isolated UTF-16 surrogates.
- File paths stay local; only file bytes cross the network.
- Bound bodies, concurrent work and deadlines; never retry a failed analysis automatically.
- No executable downloads or implicit license acceptance.
- Keep transport code independent of morphology and Lucene.

## Validation

```text
./gradlew :mystem4j-http-client:test :mystem4j-http-server:test
./gradlew check
```

Use an explicit executable for real HTTP/native integration tests.
