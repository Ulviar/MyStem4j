# Use Lucene analyzer

Use `mystem4j-lucene` to feed MyStem-based tokens into Lucene indexing or query
analysis.

## Add the module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:0.1.0")
}
```

`mystem4j-lucene` uses Lucene `10.4.0`, requires Java 21, and exposes the runtime
and tokenization types used by its public API.

## Create an analyzer

The MyStem client passed to the analyzer must return JSON. For indexing jobs,
prefer a pooled client. Always close both the analyzer and the client, or let the
analyzer own the client with `closeClientOnClose=true`.

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

By default, the analyzer uses conservative tokenization: safe offsets, gap
recovery, lemmas, suffix forms, and fallback forms; URL/email grouping and
currency/number token types are disabled. Printed terms should include lemmas such
as `мама`, `мыть`, and `рама`.

## Index and query text

Pass the same analyzer to Lucene indexing and query-text analysis. This example
uses Lucene core classes only:

```java
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.QueryBuilder;

try (ByteBuffersDirectory directory = new ByteBuffersDirectory()) {
    try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
        Document document = new Document();
        document.add(new TextField("body", "Мама мыла раму.", Field.Store.NO));
        writer.addDocument(document);
    }

    Query query = new QueryBuilder(analyzer).createBooleanQuery("body", "мыла");
    try (DirectoryReader reader = DirectoryReader.open(directory)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs hits = searcher.search(query, 10);
        System.out.println(hits.totalHits.value());
    }
}
```

If the application uses another query builder, pass the same analyzer there too.

## Choose tokenization policy

| Policy | Use when |
| --- | --- |
| `conservative()` | you need morphology and safe offsets, without entity classification |
| `search()` | you need number and currency token types, but not URL/email grouping |
| `entityAware()` | you need URL/email grouping and expanded currency forms |

Inside the same lifecycle block, pass the policy to the analyzer:

```java
Analyzer analyzer = new MystemLuceneAnalyzer(
        client,
        MystemSearchTokenizerOptions.search());
```

Use entity-aware tokenization only when the application needs URL/email grouping,
number token types, currency token types, and currency form expansion.

```java
Analyzer analyzer = new MystemLuceneAnalyzer(
        client,
        MystemSearchTokenizerOptions.entityAware());
```

Use these constructors in the try-with-resources pattern shown above. The analyzer
closes before the client.

## Set field limits and position policy

```java
int maxInputChars = 100_000;
int maxChunkChars = 16_384;

MystemLuceneAnalysisOptions analysisOptions = new MystemLuceneAnalysisOptions(
        maxInputChars,
        maxChunkChars,
        MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS);

Analyzer analyzer = new MystemLuceneAnalyzer(
        client,
        MystemSearchTokenizerOptions.conservative(),
        analysisOptions);
```

Defaults are:

| Option | Default |
| --- | --- |
| `maxInputChars` | `1_000_000` UTF-16 code units per Lucene field |
| `maxChunkChars` | `32_768` UTF-16 code units per MyStem request |
| `positionPolicy` | `COMPACT` |

Use `COMPACT` if phrase/proximity queries should ignore punctuation and skipped
fragments. Use `PRESERVE_SKIPPED_TOKENS` if those fragments should affect positions.

## Runtime choice

For indexing, use a pooled MyStem client so several indexing threads can analyze
fields concurrently. A reusable single-process client is intended for one caller
at a time and should not be shared by concurrent Lucene analysis. One-shot clients
are safe but usually slower for large indexing jobs.

For query analysis, use the same analyzer configuration as indexing: MyStem JSON
options, `MystemSearchTokenizerOptions`, and `MystemLuceneAnalysisOptions`. Low-QPS
services can use one-shot or a small pool; high-QPS services should share a pooled
client for the analyzer lifecycle.

The Lucene tokenizer handles multiline fields by replacing CR/LF with spaces before
calling a JSON-line MyStem client and preserving offsets in the original field.
