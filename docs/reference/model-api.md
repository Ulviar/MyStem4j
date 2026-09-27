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

Each array item must be a token object. Known fields are optional, but if present
must have the following JSON types:

| Object | Field | Type when present | Value when absent |
| --- | --- | --- | --- |
| Token | `text` | String | Empty string |
| Token | `analysis` | Array of analysis objects | Empty list |
| Analysis | `lex` | String | Empty string |
| Analysis | `gr` | String | Grammar parsed from an empty string |
| Analysis | `wt` | Number, including an integer | Empty `OptionalDouble` |

Explicit `null` is rejected for every known field. A wrong type raises
`MystemJsonParseException` with the field name and JSON line/column; it does not
silently discard morphology. Empty strings and empty analysis arrays are valid.
Unknown fields and their nested contents are ignored, allowing MyStem to supply
additional metadata. Grammar tags are preserved without checking a fixed
vocabulary, and numeric weights are not restricted to a probability range.

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

Token ranges are half-open: `startOffset` is inclusive, `endOffset` is exclusive.
Both count Java UTF-16 code units in `MystemDocument.originalText()`, including when
the parser receives a `MystemPreparedText`. Unknown token offsets are represented
as `-1`; a token must have either both offsets known or both offsets unknown.

`MystemToken.text()` is the surface returned by MyStem. It can differ from the
original source slice because MyStem may omit soft hyphens and preprocessing may
replace unsafe characters. To recover the source, use the known range:

```java
if (token.hasKnownOffsets()) {
    String source = document.originalText().substring(token.startOffset(), token.endOffset());
}
```

Collections are immutable copies. Token and analysis list order is preserved;
grammeme sets have no specified iteration order. The public `MystemDocument`
constructor copies its inputs but does not validate token ranges against its
text or perform alignment.

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
become common grammemes. Right-side alternatives are split by `|`, preserving
empty alternatives and their order: `S=им|` has an `им` variant followed by an
empty variant, and `S=|` has two empty variants. A missing or blank right side
produces one empty variant. Items use `String.trim()` whitespace semantics;
outer alternative parentheses are removed without validating the tag vocabulary.

## Unicode Preparation

`MystemTextPreprocessor.prepare(String)` returns `MystemPreparedText`.

`MystemTextPreprocessor.prepareJsonLine(String)` also replaces CR/LF with spaces
for reusable JSON-line clients.

`MystemPreparedText` contains:

- original text;
- prepared text;
- non-fatal issues.

Use `originalOffsetFor(int)` to map a prepared-text offset back to the original
text. It accepts every UTF-16 position from `0` through `text().length()`, including
the end position. The mapping is monotonic and maps that end to
`originalText().length()`. Positions outside this range throw
`IllegalArgumentException`. Map both endpoints when translating a range; a
supplementary noncharacter can become one space, so prepared and original lengths
need not match.

## Issue Types

| Issue | Meaning | Suggested handling |
| --- | --- | --- |
| `UNMATCHED_TOKEN` | MyStem returned token text that could not be aligned to the original text | log, reject, or let tokenization synthesize offset-safe tokens from the original text |
| `UNPAIRED_SURROGATE` | input contained an invalid UTF-16 surrogate code unit | inspect input source; preprocessor replaces it with `U+FFFD` |
| `CONTROL_CHARACTER` | input contained an unsafe control character | preprocessor replaces it with a space |
| `NONCHARACTER` | input contained a Unicode noncharacter | preprocessor replaces it with a space |

For replacement issues, `offset` and `length` describe the original UTF-16 source
range. For `UNMATCHED_TOKEN`, `offset` is the alignment cursor and `length` is the
returned MyStem surface length. They are diagnostic context, not a matched source
range; do not use them directly for `substring`. The token's two offsets are `-1`.

## Exceptions

`MystemJsonParseException` is thrown for invalid JSON, unsupported array/object
structure, or a wrong type for a known field. Syntax and shape diagnostics include
the JSON line and column when available. An alignment failure is non-fatal and is
reported through document issues instead.
