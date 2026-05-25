# Use Lucene Analyzer

Use `mystem4j-lucene` when Lucene should index/search tokens produced from MyStem JSON output.

## Add the Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:<version>")
}
```

`mystem4j-lucene` currently uses Lucene 10.x and requires the project Java 21 baseline.

## Create an Analyzer

The MyStem client must be configured for JSON output.

```java
MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .pooled()
        .build();

Analyzer analyzer = new MystemLuceneAnalyzer(client);
```

The default analyzer uses conservative tokenization: it keeps safe offsets, gap recovery, lemmas, suffix forms, and fallback forms, but does not merge URL/email entities or expose currency/number token types.

Use entity-aware tokenization only when the application needs these signals:

```java
Analyzer analyzer = new MystemLuceneAnalyzer(
        client,
        MystemSearchTokenizerOptions.entityAware(),
        true);
```

The third constructor argument makes the analyzer close the supplied client when the analyzer is closed.

## Runtime Choice

For indexing, prefer a pooled MyStem client. A reusable single process client is not intended for concurrent analyzer use. One-shot clients are safe but usually slower for large indexing jobs.

The Lucene tokenizer handles multiline fields by replacing CR/LF with spaces before calling the JSON-line client and preserving offsets in the original field. The default field limit is `MystemLuceneTokenizer.DEFAULT_MAX_INPUT_CHARS`; use a constructor with `maxInputChars` for a stricter policy.
