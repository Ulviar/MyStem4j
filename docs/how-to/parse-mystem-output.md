# Parse MyStem output

Use `mystem4j-model` to parse MyStem JSON into Java objects with token text,
lemmas, grammar, and original string offsets.

## Add the module

For existing JSON:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-model:0.1.0")
}
```

If the same code also runs MyStem:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
    implementation("io.github.ulviar.mystem4j:mystem4j-model:0.1.0")
}
```

To get grammar and part of speech, run MyStem with JSON and grammar enabled:
`--format json -i`, or
`MystemOptions.builder().format(MystemOutputFormat.JSON).grammarInfo(true)`.
`disambiguate(true)` is optional for parsing; use it when you want MyStem's
contextual best analysis.

## Parse a runtime result

```java
import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemPreparedText;
import io.github.ulviar.mystem4j.model.MystemTextIssueType;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.nio.file.Path;

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
    MystemDocument document = parser.parse(raw.input(), raw.output());
    boolean hasUnsafeOffsets = document.issues().stream()
            .anyMatch(issue -> issue.type() == MystemTextIssueType.UNMATCHED_TOKEN)
            || document.tokens().stream().anyMatch(token -> !token.hasKnownOffsets());
    if (hasUnsafeOffsets) {
        throw new IllegalStateException("MyStem output could not be aligned to the original text.");
    }

    for (MystemToken token : document.tokens()) {
        String lemma = token.analyses().stream()
                .findFirst()
                .map(MystemAnalysis::lemma)
                .orElse("<no lemma>");
        String grammar = token.analyses().stream()
                .findFirst()
                .map(analysis -> analysis.grammar().raw())
                .orElse("<no grammar>");
        String partOfSpeech = token.analyses().stream()
                .findFirst()
                .flatMap(analysis -> analysis.grammar().partOfSpeech())
                .orElse("<no part of speech>");
        System.out.printf("%s [%d,%d] lemma=%s grammar=%s pos=%s%n",
                token.text(), token.startOffset(), token.endOffset(), lemma, grammar, partOfSpeech);
    }
}
```

Offsets are Java UTF-16 string offsets. This is also the offset coordinate system
used by Lucene.

## Parse JSON directly

```java
import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemTextIssueType;
import io.github.ulviar.mystem4j.model.MystemToken;

String originalText = "Мама мыла раму";
String json = """
        [
          {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"Мама"},
          {"analysis":[{"lex":"мыть","gr":"V,несов,пе=прош,ед,изъяв,жен"}],"text":"мыла"},
          {"analysis":[{"lex":"рама","gr":"S,жен,неод=вин,ед"}],"text":"раму"}
        ]
        """;

MystemDocument document = new MystemJsonParser().parse(originalText, json);
boolean hasUnsafeOffsets = document.issues().stream()
        .anyMatch(issue -> issue.type() == MystemTextIssueType.UNMATCHED_TOKEN)
        || document.tokens().stream().anyMatch(token -> !token.hasKnownOffsets());
if (hasUnsafeOffsets) {
    throw new IllegalStateException("MyStem output could not be aligned to the original text.");
}

for (MystemToken token : document.tokens()) {
    MystemAnalysis analysis = token.analyses().stream()
            .findFirst()
            .orElse(null);
    String lemma = analysis == null ? "<no lemma>" : analysis.lemma();
    String grammar = analysis == null ? "<no grammar>" : analysis.grammar().raw();
    String partOfSpeech = analysis == null
            ? "<no part of speech>"
            : analysis.grammar().partOfSpeech().orElse("<no part of speech>");
    System.out.printf("%s [%d,%d] lemma=%s grammar=%s pos=%s%n",
            token.text(), token.startOffset(), token.endOffset(), lemma, grammar, partOfSpeech);
}
```

The parser aligns each MyStem `text` field against the original input from left to
right. MyStem JSON can omit punctuation or other copied fragments depending on
MyStem options. If a token cannot be aligned, its offsets are `-1` and the document
contains a `UNMATCHED_TOKEN` issue. Search tokenization can synthesize offset-safe
fallback tokens from the original text, or you can reject these documents with
`MystemUnmatchedTokenPolicy.FAIL`.

## Prepare text before MyStem

Preprocess input that may contain unusual Unicode values or must be sent through a
JSON-line client without CR/LF.

```java
String input = "Первая строка\nвторая строка";
MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine(input);
MystemJsonParser parser = new MystemJsonParser();

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .grammarInfo(true)
                .disambiguate(true)
                .build())
        .session()
        .build()) {
    MystemRawResult raw = client.analyze(prepared.text());
    MystemDocument document = parser.parse(prepared, raw.output());
}
```

The preprocessor keeps offsets mapped to the original Java string. Use the
`parse(MystemPreparedText, String)` overload when returned offsets must refer to
the original text. Passing `prepared.text()` as a plain string returns offsets in
prepared-text coordinates.
