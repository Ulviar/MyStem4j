# Changelog

## 0.1.0 - 2026-05-25

Initial public release.

- Added runtime clients for one-shot MyStem calls, reusable JSON sessions, pooled JSON sessions, and file requests.
- Added typed MyStem options, executable probing, and runtime exceptions.
- Added JSON parsing into Java model objects with lemmas, grammar data, and original Java string offsets.
- Added Unicode input preparation for offset-sensitive pipelines.
- Added search-token preparation with conservative, search, and entity-aware policies.
- Added Lucene `Analyzer` and `Tokenizer` integration for MyStem-backed indexing and query analysis.
- Added Kotlin DSL helpers for runtime clients and options.
- Added a Gradle plugin that downloads, verifies, extracts, probes, and wires the native MyStem executable.
