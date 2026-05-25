# Use Lucene Analyzer

Use `mystem4j-lucene` when Lucene should index or query tokens produced from
MyStem JSON output.

## Add The Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:0.1.0")
}
```

`mystem4j-lucene` uses Lucene `10.4.0`, requires Java 21, and exposes the runtime
and tokenization types used by its public API.

## Create An Analyzer

The supplied MyStem client must return JSON. For indexing jobs, prefer a pooled
client. Always close both the analyzer and the client, or let the analyzer own the
client with `closeClientOnClose=true`.

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import io.github.ulviar.mystem4j.lucene.MystemLucenePositionPolicy;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .pooled()
        .build();
     Analyzer analyzer = new MystemLuceneAnalyzer(client)) {
    try (TokenStream stream = analyzer.tokenStream("body", "Мама мыла раму.")) {
        CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsets = stream.addAttribute(OffsetAttribute.class);

        stream.reset();
        while (stream.incrementToken()) {
            System.out.printf("%s [%d,%d]%n",
                    term.toString(), offsets.startOffset(), offsets.endOffset());
        }
        stream.end();
    }
}
```

The default analyzer uses conservative tokenization: it keeps safe offsets, gap
recovery, lemmas, suffix forms, and fallback forms, but it does not merge URL/email
entities or expose currency/number token types. Printed terms should include lemma
forms such as `мама`, `мыть`, and `рама`.

## Choose Tokenization Policy

| Policy | Use when |
| --- | --- |
| `conservative()` | you need morphology and safe offsets, without entity classification |
| `search()` | you need number and currency token types, but not URL/email grouping |
| `entityAware()` | you need URL/email grouping and expanded currency forms |

Example with the middle `search()` policy:

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .pooled()
        .build();
     Analyzer analyzer = new MystemLuceneAnalyzer(
             client,
             MystemSearchTokenizerOptions.search())) {
    // Use analyzer for indexing or query analysis.
}
```

Use entity-aware tokenization only when the application needs URL/email grouping,
number token types, currency token types, and currency form expansion.

```java
try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .pooled()
        .build();
     Analyzer analyzer = new MystemLuceneAnalyzer(
             client,
             MystemSearchTokenizerOptions.entityAware())) {
    // Use analyzer for indexing or query analysis.
}
```

This example keeps client ownership explicit: the try-with-resources block closes
the analyzer and then the client.

## Set Field Limits And Position Policy

```java
int maxInputChars = 100_000;
int maxChunkChars = 16_384;

MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
        maxInputChars,
        maxChunkChars,
        MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS);

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .pooled()
        .build();
     Analyzer analyzer = new MystemLuceneAnalyzer(
             client,
             MystemSearchTokenizerOptions.conservative(),
             analysisOptions)) {
    // Use analyzer for indexing or query analysis.
}
```

Defaults are:

| Option | Default |
| --- | --- |
| `maxInputChars` | `1_000_000` UTF-16 code units per Lucene field |
| `maxChunkChars` | `32_768` UTF-16 code units per MyStem request |
| `positionPolicy` | `COMPACT` |

`COMPACT` ignores skipped separator/other tokens when computing Lucene position
increments. Choose it when phrase/proximity queries should behave as if punctuation
and other skipped fragments were not present. `PRESERVE_SKIPPED_TOKENS` adds
position gaps for skipped tokens. Choose it when phrase/proximity queries should
respect skipped fragments in the original text.

## Runtime Choice

For indexing, use a pooled MyStem client so several indexing threads can analyze
fields concurrently. A reusable single-process client is intended for one caller
at a time and should not be shared by concurrent Lucene analysis. One-shot clients
are safe but usually slower for large indexing jobs.

For query analysis, use the same tokenization policy as indexing. Low-QPS services
can use one-shot or a small pool; high-QPS services should share a pooled client
for the analyzer lifecycle.

The Lucene tokenizer handles multiline fields by replacing CR/LF with spaces before
calling a JSON-line MyStem client and preserving offsets in the original field.
