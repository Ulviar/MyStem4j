# Testing Strategy

The project is testable only if each module protects its own contract and the
cross-module paths are checked separately. Do not use one large integration suite
as a substitute for focused unit tests.

## Test layers

- Unit tests: fast tests for class invariants, parsers, option validation, and
  deterministic token output. They must not require the native MyStem binary.
- Contract tests: tests for public API behavior, JPMS metadata, Maven scopes,
  Gradle task wiring, and Lucene token stream contracts.
- Stress tests: bounded randomized or exhaustive checks for parser robustness,
  Unicode offsets, tokenization invariants, memory retention, and Lucene token
  lifecycle. Jazzer fuzz tests run in normal regression mode by default and can
  be switched to active fuzzing with `JAZZER_FUZZ=1`.
- Real-MyStem tests: opt-in tests that run with `-Dmystem4j.executable=...`.
  They validate assumptions about the external binary without making normal
  `test` depend on MyStem.
- Benchmarks: JMH smoke and compile checks. Benchmarks are not correctness
  tests, but their sample data must have a small correctness test.

## Repository gates

- `unitTest`: aggregate module unit and contract tests that do not require a real
  MyStem executable.
- `memorySmokeTest`: small-heap retention checks for model, runtime,
  tokenization, and Lucene.
- `realMystemTest`: real-MyStem integration tests.
- `realMystemUnicodeStress`: exhaustive real-MyStem Unicode offset check.
- `jmhCompileCheck` and `jmhSmoke`: benchmark wiring.
- `coverageReport`: JaCoCo reports for published modules and the Gradle plugin.
- `jpmsSmokeTest`, `publicationMetadataCheck`, `apiSurfaceCheck`: release
  metadata and public API gates.
- `spotlessCheck`, `markdownLocalLinksCheck`: repository hygiene gates.
- Gradle dependency lockfiles keep resolved dependency versions stable. Update
  them with `--write-locks` only when dependency changes are intentional.

## Module contracts

`mystem4j-model`

- Parsed token offsets use Java UTF-16 indices.
- `MystemPreparedText` maps every prepared offset back to the original string.
- JSON parsing preserves input order, analyses, grammar, weights, and text
  issues.
- Parser fuzz-style and Jazzer tests must cover generated valid Unicode JSON,
  malformed JSON-like input, and random grammar strings. Malformed parser input
  must fail with parser exceptions, not arbitrary runtime failures.
- Model collections are defensive copies.

`mystem4j-tokenization`

- Every emitted search token has non-empty forms, valid monotonic offsets, and
  `token.text() == original.substring(startOffset, endOffset)`.
- Golden tests must assert full token sequences, not only selected tokens.
- Each preset (`conservative`, `search`, `entityAware`) needs behavior checks.
- Gap synthesis, overlapping MyStem tokens, suffix recovery, URL/email merging,
  number classification, currency expansion, and Unicode marks are separate
  test concerns.

`mystem4j-lucene`

- Lucene token streams must pass Lucene test framework lifecycle checks with
  strict random-data offset validation.
- Offsets must remain correct through chunking and `CharFilter` correction.
- Chunking must not split UTF-16 surrogate pairs.
- Analyzer ownership rules must close supplied clients only when requested.

`mystem4j-runtime`

- Builders must validate invalid option combinations before requests run.
- One-shot, session, and pool modes must map failures to stable exceptions.
- Close, timeout, and pool acquisition paths must release process resources.
- Session and pool stderr backlog must be bounded and fail with
  `MystemOutputLimitException`, not hang or grow without limit.
- Result metadata and default methods must preserve order and request stats.
- Executable fake processes should be JVM-based launchers, not POSIX-only shell
  scripts, so runtime contracts remain testable on Windows.

`mystem4j-gradle-plugin`

- The plugin must not download MyStem without explicit opt-in and license
  acceptance.
- Download cache, checksum, extraction, probe, and test wiring are separate
  contracts.
- Exposed providers such as `preparedExecutable` must be task-backed and usable
  by custom build tasks.
- Probe-task fake executables should be JVM-based. Archive fixtures may still
  use platform-layout test files when the test checks extraction or provider
  wiring rather than executing the extracted binary on every OS.

`mystem4j-kotlin`

- Kotlin DSL helpers must delegate to the Java runtime without changing
  validation rules.
- Kotlin-friendly overloads must compile and preserve Java defaults.
- Public API baseline must capture JVM-visible signatures.

`mystem4j-benchmarks`

- JMH classes must compile.
- Sample data setup must produce valid parser, tokenizer, and Lucene option
  results before benchmarks are run.

## Real-MyStem policy

Keep normal unit tests deterministic and independent from the native binary.
Place tests that require MyStem in dedicated source sets or opt-in tasks. Real
MyStem tests should assert library invariants and protocol assumptions, not
dictionary-specific morphology except where the assertion is intentionally
loose.
