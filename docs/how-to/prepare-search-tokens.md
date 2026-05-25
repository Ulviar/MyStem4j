# Prepare Search Tokens

Use `mystem4j-tokenization` when parsed MyStem model objects should become search-oriented tokens before a Lucene layer.

## Add the Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-model:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-tokenization:<version>")
}
```

## Tokenize a Parsed Document

```java
MystemDocument document = new MystemJsonParser().parse(originalText, json);
List<MystemSearchToken> tokens = new MystemSearchTokenizer().tokenize(document);
```

The default tokenizer is conservative. It preserves safe offsets, gaps, lemmas, suffixes, and fallback forms, but does not merge or expose URL/email/currency/number entities as semantic token types.

Enable entity-aware behavior explicitly when the target search application needs these signals:

```java
MystemSearchTokenizer tokenizer =
        new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());
List<MystemSearchToken> tokens = tokenizer.tokenize(document);
```

Each `MystemSearchToken` contains:

- original-text surface;
- Java UTF-16 start/end offsets;
- forms for search;
- a coarse token type.

The tokenizer always handles offset-sensitive MyStem quirks such as soft hyphen ranges, `+`/`++`/`#` suffixes, omitted gaps, and exceptional diacritic removal. URL/email grouping and currency form expansion are policy-controlled options.

If MyStem output omits non-copy input fragments, the tokenizer synthesizes gap tokens from the original text. This allows URL punctuation, email `@`, currencies, and numbers to survive even when the MyStem request was not made with `copyInput`.

Unknown model offsets are rejected because Lucene cannot safely emit a token without valid offsets.
