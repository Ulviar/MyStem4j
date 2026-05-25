# Lucene API Reference

Package: `io.github.ulviar.mystem4j.lucene`

Artifact: `mystem4j-lucene`

## Compatibility

The module currently depends on Lucene `10.4.0` and requires Java 21.

## Analyzer

`MystemLuceneAnalyzer` extends Lucene `Analyzer`.

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

The supplied `MystemClient` must return JSON output. For concurrent indexing, use one-shot or pooled runtime clients.

The analyzer does not close the client by default. Use the constructor with `closeClientOnClose=true` when the analyzer should own the client.

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

Multiple forms of one search token are emitted at the same offsets. The first form uses the current token position
increment; additional forms have position increment `0`.

`SEPARATOR` and `OTHER` search-token types are not emitted to the Lucene token stream.

`MystemLuceneAnalysisOptions` controls:

- `maxInputChars`;
- `maxChunkChars`;
- `positionPolicy`.

`MystemLucenePositionPolicy.COMPACT` keeps previous behavior: skipped `SEPARATOR` and `OTHER` tokens do not add Lucene
position gaps. `PRESERVE_SKIPPED_TOKENS` increments the next emitted token position for each skipped token.
