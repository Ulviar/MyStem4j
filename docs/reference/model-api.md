# Model API Reference

Package: `io.github.ulviar.mystem4j.model`

Artifact: `io.github.ulviar.mystem4j:mystem4j-model:0.1.0`

## Parser

`MystemJsonParser` parses MyStem JSON output into a `MystemDocument`.

```java
MystemDocument parse(String originalText, String json)
MystemDocument parse(MystemPreparedText preparedText, String json)
```

`parse(MystemPreparedText, String)` aligns MyStem token text against prepared text
and returns offsets mapped back to the original text.

One-shot/file MyStem output for multiline input may contain multiple top-level JSON
arrays. The parser accepts that stream shape and concatenates the parsed tokens in
order.

## Domain Terms

- Lemma: normalized dictionary form, such as `мыть` for `мыла`.
- Grammar string: raw MyStem tag string, such as `S,жен,од=им,ед`.
- Grammeme: one feature inside a grammar string, such as `жен`, `од`, `им`, or `ед`.
- Grammar variant: one alternative after `=`, split by `|` when MyStem returns alternatives.

More definitions are in the [glossary](glossary.md).

## Document Model

- `MystemDocument` - original text, parsed tokens, and non-fatal text issues.
- `MystemToken` - MyStem `text`, Java UTF-16 start/end offsets, and analyses.
- `MystemAnalysis` - lemma, parsed grammar, and optional weight.
- `MystemGrammar` - raw grammar string, optional part of speech, common grammemes, and variants.
- `MystemGrammarVariant` - grammemes for one inflection alternative.

Unknown token offsets are represented as `-1`. A token must have either both offsets
known or both offsets unknown.

## Grammar Parsing

`MystemGrammarParser.parse(String)` parses strings such as:

- `S,жен,од=им,ед`
- `A=вин,ед,полн,муж,неод|им,ед,полн,муж`
- `PR=`

For `S,жен,од=им,ед`:

- `S` is treated as the part of speech;
- `жен` and `од` are common grammemes;
- `им` and `ед` are grammemes of one variant.

The first left-side item is treated as part of speech. Remaining left-side items
become common grammemes. Right-side alternatives are split by `|`.

## Unicode Preparation

`MystemTextPreprocessor.prepare(String)` returns `MystemPreparedText`.

`MystemTextPreprocessor.prepareJsonLine(String)` also replaces CR/LF with spaces
for reusable JSON-line clients.

`MystemPreparedText` contains:

- original text;
- prepared text;
- non-fatal issues.

Use `originalOffsetFor(int)` to map a prepared-text offset back to the original
text.

## Issue Types

| Issue | Meaning | Suggested handling |
| --- | --- | --- |
| `UNMATCHED_TOKEN` | MyStem returned token text that could not be aligned to the original text | log or reject before offset-sensitive tokenization/Lucene |
| `UNPAIRED_SURROGATE` | input contained an invalid UTF-16 surrogate code unit | inspect input source; preprocessor replaces it with `U+FFFD` |
| `CONTROL_CHARACTER` | input contained an unsafe control character | preprocessor replaces it with a space |
| `NONCHARACTER` | input contained a Unicode noncharacter | preprocessor replaces it with a space |

## Exceptions

`MystemJsonParseException` is thrown when JSON cannot be parsed as one or more
MyStem JSON arrays.
