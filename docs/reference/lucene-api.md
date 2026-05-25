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

The supplied `MystemClient` must return JSON output. For concurrent indexing, use
one-shot or pooled runtime clients. Pooled clients are usually faster for indexing.

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

- reads the Lucene input `Reader` in bounded chunks;
- rejects input longer than `maxInputChars`;
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
- `positionPolicy`, default `COMPACT`.

`MystemLuceneAnalysisOptions.defaults()` returns the default values.
`MystemLuceneAnalysisOptions.withMaxInputChars(value)` changes only the field
length limit and keeps default chunking and position behavior.

`MystemLucenePositionPolicy.COMPACT` does not add Lucene position gaps for skipped
`SEPARATOR` and `OTHER` tokens. `PRESERVE_SKIPPED_TOKENS` increments the next
emitted token position for each skipped token.
