# Architecture

MyStem4j is split into small layers so applications can use only the part they
need. The Lucene integration is built on top of the same runtime and model layers
that are available to non-Lucene users.

## Module Layers

- `mystem4j-runtime` starts MyStem processes and returns raw MyStem output.
- `mystem4j-model` parses MyStem JSON into Java model objects and aligns token offsets.
- `mystem4j-tokenization` converts parsed model objects into search-oriented tokens.
- `mystem4j-lucene` adapts search tokens to Lucene `Analyzer` and `Tokenizer` APIs.
- `mystem4j-kotlin` adds Kotlin DSL helpers over the runtime API.
- `mystem4j-gradle-plugin` prepares the native MyStem executable during builds.

The runtime has no Lucene dependency and does not parse MyStem output into model
objects. The model layer has no process-management code. The tokenization layer has
no Lucene dependency. This keeps the lower modules usable in applications that do
not use Lucene.

## MyStem Binary Boundary

MyStem is a native executable with its own license. MyStem4j artifacts do not
bundle it. Runtime clients accept an executable path or resolve one from
configuration. The Gradle plugin is the only MyStem4j component that downloads
MyStem, and it requires explicit license acceptance before doing so.

## Unicode And Offsets

Lucene offsets are Java UTF-16 offsets. For example, the Java string
`"а😀б"` has length `4` because the emoji uses two UTF-16 code units. MyStem4j
therefore treats Java UTF-16 indices as the public coordinate system.

`MystemTextPreprocessor` prepares input that may be unsafe for MyStem, such as
unpaired surrogate code units or control characters, while keeping a mapping back
to the caller's original Java string. `MystemJsonParser` then aligns MyStem token
text against the original or prepared text and returns offsets in original-text
coordinates when the prepared-text overload is used.

## Search Tokenization

MyStem JSON is not yet a search token stream. It can omit punctuation when `copyInput`
is disabled, return lemmas that differ from token text, or represent some surface
forms in a way that is awkward for search. `mystem4j-tokenization` converts
`MystemDocument` values into tokens with original offsets and search forms.

Offset safety, gap synthesis, suffix recovery, and fallback forms are baseline
behavior. Semantic enrichment such as number token types, URL/email merging, and
currency expansion is controlled by `MystemSearchTokenizerOptions`, so applications
can choose a conservative morphology pipeline or a richer entity-aware pipeline.

## Process Modes

One-shot mode starts a new MyStem process per request. It supports JSON, XML, and
text output because process exit defines the response boundary.

Reusable and pooled modes keep MyStem processes open and use JSON-line framing:
one input line maps to one JSON output line. These modes are JSON-only and reject
raw multiline input. Pooled mode is the expected base for high-throughput indexing
because it avoids process startup for every field while allowing concurrent callers.

## Error Model

Runtime failures are mapped to `MystemException` subclasses. Callers can distinguish
executable resolution failures, startup failures, request timeouts, non-zero MyStem
exits, protocol failures, output limits, pool exhaustion, closed clients, and invalid
options.

## Lucene Layer

`MystemLuceneTokenizer` reads Lucene field text, prepares offset-sensitive input,
calls a JSON MyStem client, parses model objects, prepares search tokens, and emits
Lucene attributes. `MystemLuceneAnalyzer` wires that tokenizer into Lucene's
`Analyzer` API.

The Lucene module depends on Lucene `10.4.0` and follows the project Java 21
baseline.
