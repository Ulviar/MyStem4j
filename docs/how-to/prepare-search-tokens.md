# Prepare search tokens

Use `mystem4j-tokenization` when parsed MyStem model objects should become tokens
for a custom search pipeline or for code that runs before a Lucene adapter.

## Add the module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-model:0.1.0")
    implementation("io.github.ulviar.mystem4j:mystem4j-tokenization:0.1.0")
}
```

## Tokenize a parsed document

```java
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.tokenization.MystemLemmaSelectionPolicy;
import io.github.ulviar.mystem4j.tokenization.MystemSearchToken;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import io.github.ulviar.mystem4j.tokenization.MystemTokenForm;
import io.github.ulviar.mystem4j.tokenization.MystemUnmatchedTokenPolicy;
import java.util.List;
import java.util.stream.Collectors;

String originalText = "Мама мыла раму";
String json = """
        [
          {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"Мама"},
          {"analysis":[{"lex":"мыть","gr":"V,несов,пе=прош,ед,изъяв,жен"}],"text":"мыла"},
          {"analysis":[{"lex":"рама","gr":"S,жен,неод=вин,ед"}],"text":"раму"}
        ]
        """;

MystemDocument document = new MystemJsonParser().parse(originalText, json);
List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);

for (MystemSearchToken token : tokens) {
    String forms = token.forms().stream()
            .map(MystemTokenForm::text)
            .collect(Collectors.joining(", "));
    System.out.printf("%s [%d,%d] %s%n",
            token.text(), token.startOffset(), token.endOffset(), forms);
}
```

Example output shape:

```text
Мама [0,4] мама
мыла [5,9] мыть
раму [10,14] рама
```

Exact forms depend on MyStem output and tokenizer options.

## Choose a policy

The no-argument tokenizer uses `MystemSearchTokenizerOptions.conservative()`.
Conservative tokenization keeps safe offsets, gap recovery, lemmas, suffix forms,
and fallback forms, but it does not classify numbers or merge URL/email entities.

Use `search()` when you want number and currency token types without URL/email
merging:

```java
MystemSearchTokenizer tokenizer =
        new MystemSearchTokenizer(MystemSearchTokenizerOptions.search());
```

Use `entityAware()` only when the application needs full URL/email grouping and
currency form expansion:

```java
MystemSearchTokenizer tokenizer =
        new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());
```

Each `MystemSearchToken` contains:

- source text;
- Java UTF-16 start/end offsets;
- one or more forms for search;
- a coarse token type such as `WORD`, `NUMBER`, `URL`, `EMAIL`, or `CURRENCY`.

## Offset safety

Search tokens always have valid Java UTF-16 offsets. If the parsed model contains
an unaligned MyStem token with `-1` offsets, the default policy skips that model
token and synthesizes fallback tokens from the original text.

Use strict mode when a single unaligned token should reject the document:

```java
MystemSearchTokenizerOptions options = MystemSearchTokenizerOptions.entityAware()
        .toBuilder()
        .unmatchedTokenPolicy(MystemUnmatchedTokenPolicy.FAIL)
        .build();

MystemSearchTokenizer tokenizer = new MystemSearchTokenizer(options);
```

Use weighted lemma selection when MyStem output includes `wt` and the index should
keep only one lemma per token:

```java
MystemSearchTokenizerOptions options = MystemSearchTokenizerOptions.conservative()
        .toBuilder()
        .lemmaSelectionPolicy(MystemLemmaSelectionPolicy.BEST_WEIGHT)
        .build();

MystemSearchTokenizer tokenizer = new MystemSearchTokenizer(options);
```

MyStem can omit some input fragments from JSON output depending on its options. The
tokenizer synthesizes gap tokens from the original text so punctuation, URL glue,
email `@`, currency signs, and similar fragments can still be handled. Handling for
soft hyphens, lemma suffixes such as `+`, `++`, and `#`, and selected fallback-form
normalization is built in; applications only choose whether to enable semantic
entity enrichment.
