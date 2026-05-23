# Model API Reference

Package: `io.github.ulviar.mystem4j.model`

Artifact: `mystem4j-model`

## Parser

`MystemJsonParser` parses MyStem JSON output into a `MystemDocument`.

```java
MystemDocument parse(MystemRawResult result)
MystemDocument parse(String originalText, String json)
```

`parse(MystemRawResult)` requires `MystemOutputFormat.JSON`.

The implementation uses Jackson Core's streaming parser. It does not depend on `jackson-databind`.

## Document Model

- `MystemDocument` - original text, parsed tokens, and non-fatal text issues.
- `MystemToken` - MyStem `text`, Java string start/end offsets, and analyses.
- `MystemAnalysis` - lemma, parsed grammar, and optional weight.
- `MystemGrammar` - raw grammar string, optional part of speech, common grammemes, and variants.
- `MystemGrammarVariant` - grammemes for one inflection alternative.

Unknown token offsets are represented as `-1`.

## Grammar Parsing

`MystemGrammarParser.parse(String)` parses strings such as:

- `S,жен,од=им,ед`
- `A=вин,ед,полн,муж,неод|им,ед,полн,муж`
- `PR=`

The first left-side item is treated as part of speech. Remaining left-side items become common grammemes. Right-side alternatives are split by `|`.

## Unicode Preparation

`MystemTextPreprocessor.prepare(String)` returns `MystemPreparedText`.

`MystemPreparedText` contains:

- original text;
- prepared text;
- offset mappings from prepared text to original text;
- non-fatal issues.

Issue types:

- `UNMATCHED_TOKEN`
- `UNPAIRED_SURROGATE`
- `CONTROL_CHARACTER`

## Exceptions

`MystemJsonParseException` is thrown when JSON cannot be parsed as a MyStem JSON array. It extends `MystemException`.
