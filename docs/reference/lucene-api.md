# Lucene API Reference

Package: `io.github.ulviar.mystem4j.lucene`

Artifact: `mystem4j-lucene`

## Compatibility

The module currently depends on Lucene `9.12.3`.

Reason: MyStem4j targets Java 17, while Lucene 10.x requires Java 21.

## Analyzer

`MystemLuceneAnalyzer` extends Lucene `Analyzer`.

Constructors:

```java
new MystemLuceneAnalyzer(MystemClient client)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options)
new MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose)
```

The supplied `MystemClient` must return JSON output. For concurrent indexing, use one-shot or pooled runtime clients.

The analyzer does not close the client by default. Use the constructor with `closeClientOnClose=true` when the analyzer should own the client.

## Tokenizer

`MystemLuceneTokenizer` extends Lucene `Tokenizer`.

Constructors:

```java
new MystemLuceneTokenizer(MystemClient client)
new MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options)
```

The tokenizer:

- reads the full Lucene input `Reader`;
- prepares unsafe Unicode input before sending it to MyStem;
- calls the MyStem JSON client;
- parses MyStem output through `mystem4j-model`;
- converts model tokens through `mystem4j-tokenization`;
- emits Lucene `CharTermAttribute`, `OffsetAttribute`, `PositionIncrementAttribute`, `TypeAttribute`, and `KeywordAttribute`.

Multiple forms of one search token are emitted at the same offsets. The first form has position increment `1`; additional forms have position increment `0`.

`SEPARATOR` and `OTHER` search-token types are not emitted to the Lucene token stream.
