# Changelog

## 0.1.0 - 2026-05-25

Initial public release.

- Added MyStem CLI runtime clients: one-shot, reusable JSON-line session, pooled session, file requests, probing, and typed runtime exceptions.
- Added model parsing for MyStem JSON output, grammar parsing, Unicode preparation, and original-text offset alignment.
- Added search token preparation with conservative and entity-aware policies.
- Added Lucene analyzer/tokenizer integration with bounded chunking, position policy controls, and original offset preservation.
- Added Kotlin DSL helpers.
- Added Gradle plugin for downloading, caching, extracting, probing, and wiring the native MyStem binary.
- Added JPMS descriptors for library artifacts and release gates for JPMS smoke, publication metadata, API surface, Unicode stress, memory smoke, and JMH smoke.
