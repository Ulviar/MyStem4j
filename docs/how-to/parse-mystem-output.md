# Parse MyStem Output

Use `mystem4j-model` when MyStem JSON should become Java objects with token text,
lemmas, grammar data, and original Java string offsets.

## Add The Module

For existing JSON:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-model:0.1.0")
}
```

For a pipeline that also runs MyStem:

```kotlin
dependencies {
    implementation("io.github.ulviar.mystem4j:mystem4j-runtime:0.1.0")
    implementation("io.github.ulviar.mystem4j:mystem4j-model:0.1.0")
}
```

## Parse A Runtime Result

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

    for (MystemToken token : document.tokens()) {
        String lemma = token.analyses().stream()
                .findFirst()
                .map(MystemAnalysis::lemma)
                .orElse("<no lemma>");
        String partOfSpeech = token.analyses().stream()
                .findFirst()
                .flatMap(analysis -> analysis.grammar().partOfSpeech())
                .orElse("<no part of speech>");
        System.out.printf("%s [%d,%d] lemma=%s pos=%s%n",
                token.text(), token.startOffset(), token.endOffset(), lemma, partOfSpeech);
    }
}
```

Offsets are Java UTF-16 string offsets. This is also the offset coordinate system
used by Lucene.

## Parse JSON Directly

```java
String originalText = "Мама мыла раму";
String json = """
        [
          {"analysis":[{"lex":"мама","gr":"S,жен,од=им,ед"}],"text":"Мама"},
          {"analysis":[{"lex":"мыть","gr":"V,несов,пе=прош,ед,изъяв,жен"}],"text":"мыла"},
          {"analysis":[{"lex":"рама","gr":"S,жен,неод=вин,ед"}],"text":"раму"}
        ]
        """;

MystemDocument document = new MystemJsonParser().parse(originalText, json);
```

The parser aligns each MyStem `text` field against the original input from left to
right. MyStem JSON can omit punctuation or other copied fragments depending on
MyStem options. If a token cannot be aligned, its offsets are `-1` and the document
contains a `UNMATCHED_TOKEN` issue. Log these issues or reject the document before
passing it to offset-sensitive code such as search tokenization or Lucene.

## Prepare Text Before MyStem

Use preparation when input can contain unusual Unicode values or when a JSON-line
client must receive text without CR/LF characters.

```java
String input = "Первая строка\nвторая строка";
MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine(input);
MystemJsonParser parser = new MystemJsonParser();

try (MystemClient client = Mystem.builder()
        .executable(Path.of("/path/to/mystem"))
        .options(MystemOptions.builder()
                .format(MystemOutputFormat.JSON)
                .build())
        .session()
        .build()) {
    MystemRawResult raw = client.analyze(prepared.text());
    MystemDocument document = parser.parse(prepared, raw.output());
}
```

The preprocessor keeps an offset mapping back to the caller's original Java string.
Use the `parse(MystemPreparedText, String)` overload when returned offsets must
refer to the original text. Passing `prepared.text()` as a plain string returns
offsets in prepared-text coordinates.
