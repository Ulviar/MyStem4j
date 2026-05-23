# Architecture

MyStem4j is split into layers so that Lucene integration can be built on top of a reliable CLI runtime instead of mixing process management with tokenization.

## Current Milestone

The implemented milestone contains:

- `mystem4j-runtime` - process execution and raw MyStem output;
- `mystem4j-kotlin` - Kotlin DSL over the runtime;
- `mystem4j-gradle-plugin` - MyStem binary preparation for build/test/distribution workflows.

The runtime has no Lucene dependency and does not parse MyStem output into morphology objects.

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

## Path Toward Lucene

The next layers can build on the runtime without changing process management:

- model layer: parse MyStem JSON/XML/text into stable Java/Kotlin structures;
- Unicode/tokenization layer: normalize unusual character sequences and preserve offsets;
- Lucene layer: `Tokenizer`, `TokenFilter`, `Analyzer`, and factory classes.

The Lucene layer should depend on parsed model objects and offset-aware token streams, not on raw CLI output.
