# Lucene API Reference

Package: `io.github.ulviar.mystem4j.lucene`

Artifact: `io.github.ulviar.mystem4j:mystem4j-lucene:0.1.0`

## Compatibility

The module depends on Lucene `10.5.1` and requires Java 25 or newer. Keep the
Lucene version used by the application compatible with `10.5.x`.

## Analyzer

`MystemLuceneAnalyzer` extends Lucene `Analyzer`.

Recommended constructor for most applications:

```java
new MystemLuceneAnalyzer(MystemClient client)
```

Use this when the caller owns and closes the `MystemClient`.

Constructors:

```java
new MystemLuceneAnalyzer(MystemClient client)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, int maxInputChars)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, MystemLuceneAnalysisOptions analysisOptions)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose, int maxInputChars)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose, MystemLuceneAnalysisOptions analysisOptions)
```

The supplied `MystemClient` must return JSON output. When `client.outputFormat()`
reports a known non-JSON format, analyzer construction fails immediately. Custom
clients with unknown format are accepted and must still return MyStem JSON.

For concurrent indexing, use a pooled runtime client. One-shot clients are safe
but expensive because they start a native process for each MyStem request. A reusable
single-process session serializes work through one MyStem process and is intended
for one caller at a time.

The analyzer does not close the client by default. Use a constructor with
`closeClientOnClose=true` when the analyzer should own the client.

### Single-term query normalization

`analyzer.normalize(field, text)` uses
[`MystemSearchTermNormalizer`](tokenization-api.md#search-forms), matching the
normalized aliases emitted during indexing. It removes soft hyphens and the two
exceptional acute marks and applies simple Unicode lowercasing. Existing literal
forms, including full URLs and emails, remain indexed as well.

Normalization preserves one term, including an empty term when every character is
removed. It never calls MyStem, lemmatizes a pattern or expands suffixes/synonyms.
Use normal query-text analysis for morphology. Applications constructing prefix
or wildcard queries directly should normalize their literal terms/fragments;
normalization preserves wildcard punctuation. It is not full Unicode case folding.

## Tokenizer

`MystemLuceneTokenizer` extends Lucene `Tokenizer`.

Constructors:

```java
new MystemLuceneTokenizer(MystemClient client)
new MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options)
new MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options, int maxInputChars)
new MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options, MystemLuceneAnalysisOptions analysisOptions)
```

The tokenizer:

- fails fast when the supplied client reports a known non-JSON output format;
- reads the Lucene input `Reader` in bounded chunks;
- rejects or truncates input longer than `maxInputChars`, depending on `oversizedInputPolicy`;
- limits each request to `maxChunkChars` UTF-16 code units, except that a limit of `1` allows one complete surrogate pair;
- prepares unsafe Unicode and replaces CR/LF with spaces before sending text to JSON-line MyStem clients;
- calls the MyStem JSON client;
- parses MyStem output through `mystem4j-model`;
- converts model tokens through `mystem4j-tokenization`;
- emits Lucene `CharTermAttribute`, `OffsetAttribute`, `PositionIncrementAttribute`, `TypeAttribute`, and `KeywordAttribute`.

Multiple forms of one search token are emitted at the same offsets. The first form
uses the current token position increment; additional forms have position increment
`0`.

`SEPARATOR` and `OTHER` search-token types are not emitted to the Lucene token
stream.

## Analysis Options

`MystemLuceneAnalysisOptions` controls:

- `maxInputChars`, default `1_000_000`;
- `maxChunkChars`, default `32_768`;
- `positionPolicy`, default `COMPACT`;
- `clientPolicy`, default `WARN_ON_KNOWN_SLOW_CLIENTS`;
- `oversizedInputPolicy`, default `FAIL`.

`MystemLuceneAnalysisOptions.defaults()` returns the default values.
`MystemLuceneAnalysisOptions.withMaxInputChars(value)` changes only the field
length limit and keeps default chunking, position behavior, and client policy.
Use `MystemLuceneAnalysisOptions.builder()` when changing multiple options.

Chunking never splits a UTF-16 surrogate pair and prefers whitespace boundaries.
If a single long run has no whitespace before `maxChunkChars`, the tokenizer must
split that run at a code point boundary. Offsets remain valid, but MyStem sees the
pieces as separate requests.

Chunk boundaries and token output do not depend on how many characters each
`Reader.read` call returns. A surrogate pair stays intact even when its two code
units arrive in different reads. Chunking also applies to a truncated prefix.

Offsets are half-open UTF-16 ranges in the original field, corrected through any
Lucene `CharFilter` chain. Input and chunk limits count the text after those
filters, before MyStem Unicode preparation. These are not byte, code-point, or
grapheme-cluster indices. At `TokenStream.end()`, both offsets point to the end of
the complete original field, including when only a prefix was indexed.

`MystemLucenePositionPolicy.COMPACT` does not add Lucene position gaps for skipped
`SEPARATOR` and `OTHER` tokens. It is suitable when phrase/proximity queries should
ignore skipped punctuation-like fragments. `PRESERVE_SKIPPED_TOKENS` increments the
next emitted token position for each skipped token, which makes phrase/proximity
queries respect skipped fragments in the original text.

`MystemLuceneClientPolicy.WARN_ON_KNOWN_SLOW_CLIENTS` logs a warning for built-in
one-shot and reusable-session runtime clients. Use `REQUIRE_POOLED_OR_UNKNOWN`
to reject those profiles at analyzer construction time, or `ALLOW_ANY` when the
application intentionally accepts the performance trade-off.

`MystemLuceneOversizedInputPolicy.FAIL` rejects fields longer than `maxInputChars`.
`TRUNCATE_AT_CODE_POINT_BOUNDARY` analyzes only the prefix that fits the limit and
never cuts a valid UTF-16 surrogate pair in half. Already unpaired surrogates in
the input follow the normal Unicode-preparation policy.
