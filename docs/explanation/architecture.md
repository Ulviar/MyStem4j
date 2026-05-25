# Architecture

MyStem4j is split into layers so that Lucene integration can be built on top of a reliable CLI runtime instead of mixing process management with tokenization.

## Current Milestone

The implemented milestone contains:

- `mystem4j-runtime` - process execution and raw MyStem output;
- `mystem4j-model` - JSON postprocessing, grammar parsing, token offset alignment, and Unicode preparation;
- `mystem4j-tokenization` - search-token preparation above model objects;
- `mystem4j-lucene` - Lucene analyzer/tokenizer integration;
- `mystem4j-kotlin` - Kotlin DSL over the runtime;
- `mystem4j-gradle-plugin` - MyStem binary preparation for build/test/distribution workflows.

The runtime has no Lucene dependency and does not parse MyStem output into morphology objects. The model layer is the first postprocessing layer above raw CLI output. The tokenization layer is Lucene-free and prepares forms/coarse token types. The Lucene layer adapts those prepared tokens to Lucene attributes.

## Unicode And Offsets

Lucene offsets are Java UTF-16 offsets, so the model layer treats UTF-16 code units as the public coordinate system. MyStem may classify unusual Unicode code points differently from the JVM. The model layer therefore separates two concerns:

- `MystemTextPreprocessor` prepares unsafe input for MyStem while retaining a mapping back to the original Java string;
- `MystemJsonParser` aligns MyStem output against either the original text or prepared text and returns offsets in original-text coordinates.

The test suite has fast exhaustive UTF-16 invariants for the model code and opt-in real-MyStem stress tests for empirical compatibility with the native binary.

## Search Tokenization

MyStem output is not yet a Lucene token stream. It may drop soft hyphens from surface text, attach `+` or `#` to lemmas but not token text, and omit non-copy fragments from JSON output.

`mystem4j-tokenization` converts `MystemDocument` into search-oriented tokens while preserving original offsets. Offset safety, gap synthesis, suffix recovery, and fallback forms are baseline behavior. Semantic enrichment such as number token types, URL/email merging, and currency expansion is controlled by `MystemSearchTokenizerOptions` so applications can choose a conservative morphology pipeline or a richer entity-aware search pipeline.

## Why MyStem Is External

MyStem is distributed as a native CLI binary with its own license. The runtime accepts an executable path and stays offline-friendly. The Gradle plugin is the only component that can download MyStem, and it requires explicit license acceptance.

## Process Modes

One-shot mode is the most general mode. It supports every MyStem format because the process exits and EOF defines response boundaries.

Reusable and pooled modes are limited to JSON line framing. MyStem 3.1 returns JSON for a single line request as one output line, so the runtime can safely read one response per request. Multiline input is rejected in these modes until a more explicit framing strategy is added.

Pooled mode is the expected base for high-throughput Lucene integration because it avoids per-token or per-document process startup costs while keeping request handling synchronous.

## Error Model

The runtime wraps iCLI and process failures in `MystemException` subclasses. The goal is to separate startup failures, timeouts, process exits, protocol failures, pool exhaustion, output limits, closed clients, and invalid options so callers can make stable retry/fallback decisions.

Protocol session failures keep a bounded transcript where iCLI provides one. For process exits this transcript is also used to preserve stderr-like diagnostic content.

## Gradle Preparation

The plugin prepares a project-local MyStem binary:

1. choose the platform archive;
2. download with explicit opt-in and license acceptance;
3. reuse the archive only when metadata matches;
4. extract the executable;
5. run a JSON smoke probe;
6. optionally wire the executable into tests or copy it for application distribution.

The current cache is project-local under `build/mystem`. A shared Gradle user-home cache is a future enhancement, not current behavior.

## Lucene Layer

The Lucene layer builds on the runtime, model, and tokenization modules without changing process management:

- `MystemLuceneTokenizer` reads the whole Lucene input, prepares unsafe Unicode, calls a JSON MyStem client, parses model objects, prepares search tokens, and emits Lucene attributes.
- `MystemLuceneAnalyzer` wires the tokenizer into Lucene's `Analyzer` API.

The layer depends on Lucene 10.x and the project baseline is Java 21.
