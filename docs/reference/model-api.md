# Model API Reference

Package: `io.github.ulviar.mystem4j.model`

Artifact: `mystem4j-model`

## Parser

`MystemJsonParser` parses MyStem JSON output into a `MystemDocument`.

```java
MystemDocument parse(String originalText, String json)
MystemDocument parse(MystemPreparedText preparedText, String json)
```

`parse(MystemPreparedText, String)` aligns MyStem token text against prepared text and returns offsets mapped back to the original text.

One-shot/file MyStem output for multiline input may contain multiple top-level JSON arrays. The parser accepts that stream shape and concatenates the parsed tokens in order.

## Document Model

- `MystemDocument` - original text, parsed tokens, and non-fatal text issues.
- `MystemToken` - MyStem `text`, Java string start/end offsets, and analyses.
- `MystemAnalysis` - lemma, parsed grammar, and optional weight.
- `MystemGrammar` - raw grammar string, optional part of speech, common grammemes, and variants.
- `MystemGrammarVariant` - grammemes for one inflection alternative.

Unknown token offsets are represented as `-1`. A token must have either both offsets known or both offsets unknown.

## Grammar Parsing

`MystemGrammarParser.parse(String)` parses strings such as:

- `S,жен,од=им,ед`
- `A=вин,ед,полн,муж,неод|им,ед,полн,муж`
- `PR=`

The first left-side item is treated as part of speech. Remaining left-side items become common grammemes. Right-side alternatives are split by `|`.

## Unicode Preparation

`MystemTextPreprocessor.prepare(String)` returns `MystemPreparedText`.

`MystemTextPreprocessor.prepareJsonLine(String)` also replaces CR/LF with spaces for reusable JSON-line clients.

`MystemPreparedText` contains:

- original text;
- prepared text;
- non-fatal issues.

Use `originalOffsetFor(int)` to map a prepared-text offset back to the original text.

Issue types:

- `UNMATCHED_TOKEN`
- `UNPAIRED_SURROGATE`
- `CONTROL_CHARACTER`
- `NONCHARACTER`

## Exceptions

`MystemJsonParseException` is thrown when JSON cannot be parsed as one or more MyStem JSON arrays.
