# Use Lucene analyzer

Use `mystem4j-lucene` to feed MyStem-based tokens into Lucene indexing or query
analysis. Supply a local native client or an [HTTP client](http-service.md). The
HTTP variant requires the executable only on the service host.

## Index and query text

The local example below uses Java 25 or newer and an executable MyStem 3.1 binary. To prepare the binary,
follow [Prepare MyStem with Gradle](prepare-mystem-with-gradle.md). On Apple Silicon,
the plugin's Intel macOS binary requires Rosetta; see
[the setup instructions](troubleshooting.md#mystem-reports-bad-cpu-type-on-apple-silicon).

Create `build.gradle.kts`:

```kotlin
plugins {
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-lucene:0.2.0")
}

application {
    mainClass.set("example.LuceneSearchExample")
}
```

`mystem4j-lucene` uses Lucene `10.5.1` and brings the runtime and tokenization APIs
used below. This example needs only Lucene core classes.

Create `src/main/java/example/LuceneSearchExample.java`:

<!-- lucene-search-example:start -->
```java
package example;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.QueryBuilder;

public final class LuceneSearchExample {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass the path to the MyStem executable.");
        }
        try (MystemClient client = Mystem.builder()
                .executable(Path.of(args[0]))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .disambiguate(true)
                        .build())
                .pooled()
                .build()) {
            int matches = indexAndSearch(client);
            if (matches != 1) {
                throw new IllegalStateException("Expected one matching document, got " + matches);
            }
            System.out.println("Matches: " + matches);
        }
    }

    public static int indexAndSearch(MystemClient client) throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client);
                ByteBuffersDirectory directory = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                Document document = new Document();
                document.add(new TextField("body", "Мама мыла раму.", Field.Store.NO));
                writer.addDocument(document);
            }

            Query query = new QueryBuilder(analyzer).createBooleanQuery("body", "мыть");
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                return new IndexSearcher(reader).count(query);
            }
        }
    }
}
```
<!-- lucene-search-example:end -->

Run it with the path to your MyStem executable:

```bash
./gradlew run --args="/absolute/path/to/mystem"
```

The result is `Matches: 1`: the indexed word `мыла` and the query `мыть` share a
lemma. The same analyzer handles indexing and query text. The nested blocks close
the Lucene resources; `main` owns and closes the pooled client.

The client must return JSON. A built-in client configured for `TEXT` or `XML` is
rejected when creating the analyzer. By default, the analyzer uses conservative
tokenization: offsets, gap recovery, lemmas, suffix forms, and fallback forms;
URL/email grouping and currency/number token types are disabled.

If the application uses another query builder, pass the same analyzer there too.

## Choose tokenization policy

| Policy | Use when |
| --- | --- |
| `conservative()` | you need morphology and safe offsets, without entity classification |
| `search()` | you need number and currency token types, but not URL/email grouping |
| `entityAware()` | you need URL/email grouping and expanded currency forms |

In `indexAndSearch`, replace the analyzer constructor to select a policy:

```java
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;

// Use this constructor in the existing try-with-resources declaration.
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

Add the imports below to the file, then configure these options inside
`indexAndSearch`, before opening the analyzer:

```java
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions;
import io.github.ulviar.mystem4j.lucene.MystemLuceneClientPolicy;
import io.github.ulviar.mystem4j.lucene.MystemLuceneOversizedInputPolicy;
import io.github.ulviar.mystem4j.lucene.MystemLucenePositionPolicy;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;

int maxInputChars = 100_000;
int maxChunkChars = 16_384;

MystemLuceneAnalysisOptions analysisOptions = MystemLuceneAnalysisOptions.builder()
        .maxInputChars(maxInputChars)
        .maxChunkChars(maxChunkChars)
        .positionPolicy(MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS)
        .clientPolicy(MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN)
        .oversizedInputPolicy(MystemLuceneOversizedInputPolicy.FAIL)
        .build();

Analyzer analyzer = new MystemLuceneAnalyzer(
        client,
        MystemSearchTokenizerOptions.conservative(),
        analysisOptions);
```

Use this analyzer constructor in the existing try-with-resources declaration.

Defaults are:

| Option | Default |
| --- | --- |
| `maxInputChars` | `1_000_000` UTF-16 code units per Lucene field |
| `maxChunkChars` | `32_768` UTF-16 code units per MyStem request |
| `positionPolicy` | `COMPACT` |
| `clientPolicy` | `WARN_ON_KNOWN_SLOW_CLIENTS` |
| `oversizedInputPolicy` | `FAIL` |

Use `COMPACT` if phrase/proximity queries should ignore punctuation and skipped
fragments. Use `PRESERVE_SKIPPED_TOKENS` if those fragments should affect positions.
Use `REQUIRE_POOLED_OR_UNKNOWN` for indexing jobs where accidentally passing a
one-shot or reusable-session runtime client should fail during analyzer creation.
Use `TRUNCATE_AT_CODE_POINT_BOUNDARY` only when silently indexing a bounded prefix
is preferable to rejecting an oversized field.

Chunking prefers whitespace and never splits a UTF-16 surrogate pair. If a field
contains one very long run without whitespace, that run can be split at a code
point boundary; offsets still point to the original field.
With `maxChunkChars=1`, a complete surrogate pair needs a two-unit request. The
same chunk limit applies when indexing a truncated prefix. After truncation, the
stream's final offset still marks the end of the full original field.

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
