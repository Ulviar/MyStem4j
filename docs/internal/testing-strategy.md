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
- Context and metamorphic tests: generated inputs place Unicode code points before,
  after, around, inside, and between Russian dictionary-like words, unknown Russian
  words, non-Russian words, and numbers. Assertions use independent offset,
  partition, determinism, and shift invariants. These checks complement exact
  morphology and search-form expectations; they do not prove them.
- Observed-behavior fixtures: compact MyStem 3.1 JSON samples preserve known surface
  quirks such as soft hyphen, `+`, `++`, `+++`, `#`, and `##`. Unit tests replay the
  samples without the native binary and assert exact forms and lemma-to-source
  associations. Real-MyStem checks verify stable examples with copied input both
  enabled and disabled; contextual morphology can vary with other binary versions.
- Real-MyStem tests: opt-in tests that run with `-Dmystem4j.executable=...`.
  They validate assumptions about the external binary without making normal
  `test` depend on MyStem.
  Lucene's native suites exclude only idle shared Procwright retirement and PTY
  executor threads from leak detection. Active cleanup, callbacks and process
  stream threads remain visible; runtime lifecycle tests and the pool soak check
  that client-owned processes and file descriptors are released.
- Benchmarks: JMH smoke and compile checks. Benchmarks are not correctness
  tests, but their sample data must have a small correctness test.

## Repository gates

- `unitTest`: aggregate module unit and contract tests that do not require a real
  MyStem executable.
- `memorySmokeTest`: small-heap retention checks for model, runtime,
  tokenization, and Lucene.
- `realMystemTest`: real-MyStem integration tests.
- `realMystemUnicodeStress`: exhaustive real-MyStem Unicode offset check.
- `realMystemPoolSoak`: sustained real-MyStem load at 2x, 4x, and 8x pool
  concurrency with worker rotation, response ownership, p95/p99, process-count,
  process-release, and open-file-descriptor checks.
- `unicodeContextStressTest`: Java-side tokenization invariants for every Unicode
  scalar value with deterministic context rotation, plus the complete context
  matrix for every defined non-letter and non-decimal code point.
- `jmhCompileCheck` and `jmhSmoke`: benchmark wiring.
- `:mystem4j-benchmarks:nativeJmh`: opt-in native requests, Lucene analysis and
  four-document index construction; see [measurement procedure](../how-to/measure-performance.md).
- `coverageReport`: JaCoCo reports for published modules and the Gradle plugin.
- `coverageVerification`: per-module JaCoCo line and branch floors that prevent
  material regression; thresholds intentionally remain below current coverage.
- `architectureCheck`: production bytecode dependencies, public API dependencies,
  JPMS exports/requires, consumer dependency graphs, isolated consumers, and POM
  dependency scopes. The allowed graph is owned by
  `buildSrc/src/main/java/io/github/ulviar/mystem4j/buildlogic/ModuleBoundaryPolicy.java`.
- `jpmsSmokeTest`: compiles and runs each of the five libraries as a separate
  consumer on both classpath and module path. Fixtures live in
  `config/module-consumers`; each resolves only its declared library and that
  library's appropriate Gradle dependency variant.
- `publicationMetadataCheck`, `apiSurfaceCheck`: artifact metadata and public API
  gates.
- `agentInfrastructureCheck`: LLM context routing, active-work lifecycle, and
  historical-document labeling.
- `spotlessCheck`, `markdownLocalLinksCheck`: repository hygiene gates.
- `documentationCheck`: builds Java/Kotlin API documentation and every module's
  Javadoc JAR, including non-published benchmark artifacts, and checks that local
  HTML navigation links resolve inside each archive. Published Java modules
  reject missing comments/tags; all Java Javadocs treat warnings as errors. Kotlin
  Dokka reports undocumented declarations and fails on warnings. Its standard HTML
  output is packaged under the `javadoc` classifier; the experimental Javadoc renderer
  is avoided because it generates broken navigation links. API comments must
  explain observable contracts (defaults, units, validation, ownership, and failure
  behavior); passing the documentation gate alone does not establish their accuracy.
- Java compilation treats actionable `-Xlint` warnings as errors; Kotlin
  compilation treats warnings as errors.
- Gradle dependency lockfiles keep resolved dependency versions stable. Update
  them with `--write-locks` only when dependency changes are intentional.
  Run each affected module's `dependencies --write-locks` task, including dependent
  modules: `check` does not resolve every configuration, such as each library's
  own `runtimeClasspath`. Inspect dependency reports for resolution failures.
  Library dependencies resolve only from Maven Central; local artifacts and private
  repository credentials do not affect the default build.

## Boundary gate tests

`buildSrc` runs its tests before assembling build logic, so root checks cannot use
untested boundary rules. Compiled adversarial fixtures verify that the gate rejects
backward module references, implementation types in generic public signatures,
process execution in the model, and networking in the runtime. Additional cases
reject leaked compile dependencies, unused runtime dependencies, extra exports,
and transitive implementation modules. Value-only URI parsing and IDN conversion
remain allowed; they do not resolve hosts or access the network.

These checks inspect direct bytecode references and resolved dependency graphs;
they are not a sandbox for reflection or behavior inside third-party libraries.
Behavioral tests still own offsets, process lifetime, and other execution contracts.

Build-tool subprocess tests exercise saturated stderr, nonzero exits, output
overflow, timeout and interruption. Captures retain at most 4 MiB per stream and
commands time out after two minutes; truncation fails the gate instead of accepting
an incomplete API or compiler result.

Convention fixtures build ordinary, source and documentation JARs with and without
an automatic module name, inspect their manifests, and verify configuration-cache
reuse. They also check Java 25 class version 69 without preview bytecode. Optional
manifest metadata must not become a required input for unrelated JAR tasks.

`PublishingConventionsTest` publishes a library, a Gradle plugin, and its marker to
a local repository without signing or Portal credentials. Its Central opt-in
fixture includes a Kotlin module and loads Kotlin and Central plugins together at
root. It checks that existing publications are preserved, signing uses GnuPG,
automatic release is disabled, and the benchmark project is not published. Its
Kotlin source archive must contain both Java and Kotlin files at package paths,
with identical bytes across task orders and configuration-cache reuse. Only the
Java component's `sourcesJar` writes that archive, and Gradle metadata lists it once.
These fixtures configure Central publishing without executing signing or upload
tasks. A maintainer verifies real detached signatures through the
[local signed-publication procedure](publication.md#check-a-signed-publication-locally).
Remote upload and Portal validation are separate release actions, not test gates.

Run the focused gate with:

```bash
./gradlew architectureCheck
```

## Module contracts

`mystem4j-model`

- Parsed token offsets use Java UTF-16 indices.
- `MystemPreparedText` maps every prepared offset back to the original string.
- Mapping composition uses independent expected coordinates for every boundary:
  all supplementary noncharacters, adjacent contractions, CR/LF, controls,
  combining marks, and unpaired surrogates, in both preparation modes.
- JSON parsing preserves input order, analyses, grammar, weights, and text
  issues.
- Present known JSON fields must have the expected type; explicit null and wrong
  types raise a field/location diagnostic. Absent optional fields and unknown
  nested metadata retain their documented behavior.
- Offset alignment tests must include repeated surfaces where an earlier token
  needs fuzzy alignment and a later occurrence is an exact match. The nearest
  compatible occurrence must win.
- Parser fuzz-style and Jazzer tests must cover generated valid Unicode JSON,
  malformed JSON-like input, and random grammar strings. Malformed parser input
  must fail with parser exceptions, not arbitrary runtime failures.
- Model collections are defensive copies.

`mystem4j-tokenization`

- Every emitted search token has non-empty forms, valid monotonic offsets, and
  `token.text() == original.substring(startOffset, endOffset)`.
- Full, sparse, and empty model output must reconstruct the entire source without
  gaps or overlaps for every preset. Invalid manually supplied ranges, including
  overlapping or out-of-order model ranges, fail before source partitioning.
- Golden tests must assert full token sequences, not only selected tokens. Repeated
  copied separators must not detach analyses from intervening words; check the
  exact lemma, keyword flag and original source occurrence, not just a partition.
- Each preset (`conservative`, `search`, `entityAware`) needs behavior checks.
- Shared term normalization preserves existing forms before unique nonempty
  aliases, their keyword flags and source ranges. Cover Turkish I, Greek sigma,
  supplementary letters, meaningful marks, idempotence and prefix stability.
- Gap synthesis, rejected overlapping model ranges, suffix recovery, URL/email merging,
  number classification, currency expansion, and Unicode marks are separate
  test concerns.
- Unicode classification needs a full context matrix for representative category
  members and boundary points. The exhaustive task distributes all scalar values
  across the same contexts and must report the code point, base text, and context
  on failure.
- Entity merging needs valid, invalid, prefix, suffix, and adjacent-punctuation
  cases. A merged range must not consume unrelated text in the same non-whitespace
  group. Cover Unicode hosts, full email local-part punctuation, malformed entity
  prefixes, and the absence of falsely shortened addresses.
- Checked-in MyStem output is an observed-behavior fixture, not an independent
  oracle. Tests derive offsets from the original string and assert complete source
  partitioning separately.

`mystem4j-lucene`

- Lucene token streams must pass Lucene test framework lifecycle checks with
  strict random-data offset validation.
- At least one randomized Lucene client must mimic MyStem omissions and character
  dropping instead of echoing the complete input as one token.
- Model/tokenization quirks with offset significance must be replayed through
  Lucene attributes and index/query round trips. Assert exact terms, keyword/type
  attributes, offsets and positions; term and phrase queries must find the expected
  source occurrences. Normalized currency queries must match expanded symbols.
- Reader-fragmentation tests cross read sizes, chunk sizes and every truncation
  boundary. They compare request text, terms, offsets, positions and final offsets;
  independently specified regressions protect against shared mistakes.
- Offsets must compose through chained `MappingCharFilter` expansion/contraction,
  Unicode preparation, dropped-character alignment, chunking and truncation.
  Posting offsets must select the original source occurrences after indexing.
- Chunking and truncation must not split UTF-16 surrogate pairs, including pairs
  whose code units arrive in different reads. Truncated prefixes remain chunked.
- Real-MyStem Lucene tests cover short reads, repeated soft-hyphen words,
  supplementary noncharacter contraction, multiline input, chunks and truncation.
- Analyzer ownership rules must close supplied clients only when requested.
- Prefix/wildcard queries must find indexed normalized aliases without a native
  call during single-term normalization; literal entity terms stay searchable.
- The complete Lucene documentation example is compiled and exercised by tests.
  An equality check against the marked Markdown snippet prevents drift; real-MyStem
  runs also execute its main method and verify the expected query result.

`mystem4j-runtime`

- Builders must validate invalid option combinations before requests run.
- One-shot, session, and pool modes must map failures to stable exceptions.
- Close, timeout, and pool acquisition paths must release process resources.
- Exact payload limits exclude the protocol newline; rejected input must leave
  every mode usable. Captured response character limits apply in all modes.
- Interruption must preserve the caller's interrupt flag and terminate the affected
  process. Concurrent close must wait for active requests in all modes.
- Pool admission must preserve waiting order and release capacity after failure.
  Interrupting a queued caller must leave the active worker and later requests usable.
  Real-MyStem integration also exercises repeated long requests from four callers
  through a single worker to expose starvation hidden by short-request throughput.
- Session and pool stderr capture must remain bounded, discarding excess stderr
  while continuing to consume stdout. One-shot stderr overflow must fail with
  `MystemOutputLimitException`. Stdout limits and request timeouts remain enforced
  in every mode.
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
- Kotlin `use` and all three DSL modes must preserve payload boundaries, recovery
  after rejected input, and rejection after close.
- Public API baseline must capture JVM-visible signatures.

`mystem4j-benchmarks`

- JMH classes must compile.
- Sample data setup must produce valid parser, tokenizer, and Lucene option
  results before benchmarks are run.
- Four distinct core fixtures cover 1,024, 16,384 and 131,072 UTF-16 units and validate
  aligned offsets. External corpus loading, empty inputs and surrogate-safe sizing
  are checked by the benchmark test. Native setup verifies an index/query round trip.
- Native tail claims require `performance_evidence.py tail` with enough samples
  across forks; RSS evidence uses its separate macOS/Linux sampler. Run the tools'
  unittest command in the performance guide before using their output.

## Real-MyStem policy

Keep normal unit tests deterministic and independent from the native binary.
Place tests that require MyStem in dedicated source sets or opt-in tasks. Real
MyStem tests should assert library invariants and protocol assumptions, not
dictionary-specific morphology except where the assertion is intentionally
loose.

The pool soak runs 10,000 requests per concurrency level by default. Override
`mystem4j.poolSoakRequests`, `mystem4j.poolSoakPoolSize`, or
`mystem4j.poolSoakMaxRequestsPerWorker` only for diagnosis; release evidence uses
the defaults.

Do not permanently disable expensive behavioral tests. Put them behind explicit
Gradle tasks or source sets, keep a representative regression subset in normal
tests, and make failures include enough input and output data to reproduce the
case.
