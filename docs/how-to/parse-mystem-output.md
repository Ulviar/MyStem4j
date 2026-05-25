# Parse MyStem Output

Use `mystem4j-model` when raw JSON from `mystem4j-runtime` should become structured tokens, analyses, grammar data, and offsets.

## Add the Module

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:<version>")
    implementation("io.github.ulviar.mystem4j:mystem4j-model:<version>")
}
```

## Parse a Runtime Result

```java
MystemJsonParser parser = new MystemJsonParser();

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .build()) {
    MystemRawResult raw = client.analyze("Мама мыла раму.");
    MystemDocument document = parser.parse(raw);

    for (MystemToken token : document.tokens()) {
        System.out.println(token.text() + " " + token.startOffset() + "-" + token.endOffset());
    }
}
```

Offsets are Java string offsets. That matches Lucene offset conventions.

## Parse JSON Directly

```java
MystemDocument document = new MystemJsonParser().parse(
        "Мама, мама.",
        """
        [
          {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"Мама"},
          {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"мама"}
        ]
        """);
```

The parser aligns each MyStem `text` field against the original input from left to right. If a token cannot be aligned, its offsets are `-1` and the document contains a `UNMATCHED_TOKEN` issue.

## Prepare Text Before MyStem

```java
MystemPreparedText prepared = MystemTextPreprocessor.prepare(input);
MystemRawResult raw = client.analyze(prepared.text());
MystemDocument document = parser.parse(prepared, raw.output());
```

The preprocessor:

- preserves valid supplementary code points;
- replaces unpaired surrogate code units with `U+FFFD`;
- replaces unsafe control characters with spaces;
- replaces Unicode noncharacters with spaces;
- records offset mappings back to the original Java string.

This keeps MyStem input safer without losing the information needed to map token offsets back to the caller's text.

Use the `MystemPreparedText` overload when offsets must refer to the caller's original text. Passing `prepared.text()` directly returns offsets in prepared-text coordinates.
