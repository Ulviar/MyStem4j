# Lucene API Reference

Package: `io.github.ulviar.mystem4j.lucene`

Artifact: `io.github.ulviar.mystem4j:mystem4j-lucene:0.1.0`

## Compatibility

The module depends on Lucene `10.4.0` and requires Java 21. Keep the Lucene version
used by the application compatible with `10.4.x`.

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
but expensive because they start a native process per analyzed field. A reusable
single-process session serializes work through one MyStem process and is intended
for one caller at a time.

The analyzer does not close the client by default. Use a constructor with
`closeClientOnClose=true` when the analyzer should own the client.

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
- sends no more than `maxChunkChars` UTF-16 code units to MyStem in one request;
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

Chunking never splits a UTF-16 surrogate pair and prefers whitespace boundaries.
If a single long run has no whitespace before `maxChunkChars`, the tokenizer must
split that run at a code point boundary. Offsets remain valid, but MyStem sees the
pieces as separate requests.

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
does not end the prefix on an unpaired UTF-16 surrogate.
