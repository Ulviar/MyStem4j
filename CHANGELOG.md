# Changelog

## Unreleased

- Require Java 25 for libraries and the Gradle plugin; select Java 25 for the repository daemon, Java/Kotlin compilation and tests, with bytecode checks.
- Update Gradle to 9.7.1, Kotlin to 2.4.20, JUnit to 6.1.3, JaCoCo to 0.8.15, Spotless to 8.10.2 and BCV to 0.18.2; use current ASM for Java 25 API validation.
- Update Lucene to 10.5.1 and Jackson Core to 2.22.2, refresh dependency locks and current setup examples.
- Share character normalization between indexed aliases and Lucene single-term queries; preserve literal forms, source offsets and synonym positions.
- Reject wrong JSON types and explicit null for known MyStem fields with field/location diagnostics while retaining optional fields and unknown metadata.
- Document model/runtime public contracts and enforce strict missing-doc checks for those APIs; build documentation JARs as part of `check`.
- Allow absent automatic module names on JAR tasks, including benchmark documentation artifacts.
- Keep the complete Lucene index/query documentation example compiled and tested, and document Rosetta as the prerequisite for Intel MyStem on Apple Silicon.
- Keep MyStem lemmas attached to their original words when suffix recovery consumes copied soft-hyphen separators; reject overlapping model ranges instead of guessing another occurrence.
- Normalize soft hyphens and exceptional diacritics before expanding suffixless word and number forms.
- Recognize Unicode URL hosts and full unquoted email local parts, normalize additional URL domain forms, and reject malformed entities without indexing shortened substitutes.
- Replay captured native output through exact term, lemma, offset, keyword and Lucene index/query assertions, with real-MyStem checks for copied and omitted separators.
- Recognize each adjacent email/URL in opt-in entity enrichment while preserving separators and URL user info/query contents.
- Skip directory candidates during PATH executable discovery.
- Measure varied document fixtures and optional application corpora; validate tail sample counts and sample external MyStem RSS separately from Java allocation.
- Admit pooled requests in waiting order so repeated callers cannot starve queued work; preserve active workers when a queued caller is interrupted.
- Apply text payload limits consistently across one-shot, session and pool modes, excluding protocol framing and preserving clients after rejected input.
- Enforce captured stdout character limits in one-shot mode and wait for active requests during one-shot close.
- Validate mode options and regular-file fixlists before executable resolution; recognize Darwin as macOS during MyStem preparation.
- Bound build-tool subprocess output and execution time, drain both streams concurrently, and terminate children on interruption.
- Add representative Java benchmarks and opt-in native request, Lucene analysis and index benchmarks with latency and allocation reporting.
- Preserve complete Unicode code points when Lucene input arrives in short reads or is truncated between surrogate code units.
- Keep truncated prefixes within the configured chunk size and preserve final offsets in the complete original field.
- Reject manually supplied model ranges that split surrogate pairs or assign empty ranges to nonempty tokens with `MystemTokenizationException`.

## 0.1.0 - 2026-05-25

Initial implementation baseline; not a published release.

- Added runtime clients for one-shot MyStem calls, reusable JSON sessions, pooled JSON sessions, and file requests.
- Added typed MyStem options, executable probing, and runtime exceptions.
- Added JSON parsing into Java model objects with lemmas, grammar data, and original Java string offsets.
- Added Unicode input preparation for offset-sensitive pipelines.
- Added search-token preparation with conservative, search, and entity-aware policies.
- Added Lucene `Analyzer` and `Tokenizer` integration for MyStem-backed indexing and query analysis.
- Added Kotlin DSL helpers for runtime clients and options.
- Added a Gradle plugin that downloads, verifies, extracts, probes, and wires the native MyStem executable.
