# Tokenization API Reference

Package: `io.github.ulviar.mystem4j.tokenization`

Artifact: `io.github.ulviar.mystem4j:mystem4j-tokenization:0.1.0`

## Search Tokenizer

`MystemSearchTokenizer` converts a `MystemDocument` into search-oriented tokens.
A search-oriented token keeps original text offsets and exposes one or more forms
that a search index may use, such as a MyStem lemma and fallback surface form.

```java
List<MystemSearchToken> tokenize(MystemDocument document)
```

The tokenizer expects known offsets. A token with `-1` offsets causes
`MystemTokenizationException`.

If the parsed MyStem output skips parts of the original text, the tokenizer
synthesizes gap tokens from the original text before URL/email grouping and form
generation.

## Options

The no-argument constructor uses `MystemSearchTokenizerOptions.conservative()`:
offsets, gaps, suffix recovery, lemmas, and fallback forms are handled, but numbers,
URLs, emails, and currencies are not exposed as semantic token types by default.

Preset options:

| Preset | Behavior |
| --- | --- |
| `conservative()` | morphology-oriented defaults, no semantic entity classification |
| `search()` | exposes numbers and currency symbols, but does not merge URL/email entities or expand currency names |
| `entityAware()` | enables number types, URL/email merging, currency types, and currency form expansion |

```java
MystemSearchTokenizer tokenizer =
        new MystemSearchTokenizer(MystemSearchTokenizerOptions.search());
```

## Token Model

- `MystemSearchToken` - source text, search forms, Java UTF-16 offsets, and type.
- `MystemTokenForm` - form text plus `keyword` flag.
- `MystemSearchTokenType` - `WORD`, `NUMBER`, `URL`, `EMAIL`, `CURRENCY`, `SEPARATOR`, or `OTHER`.

`keyword=true` means the form should be treated as already normalized and should not
be changed by later stemming or lowercasing logic. This is useful for values such
as numbers, full URLs, and currency codes.

## Search Forms

The tokenizer emits lemma forms when MyStem analysis has lemmas. When a token has
no lemma, the tokenizer emits lowercase source forms and marks them as non-keyword
for words and keyword for numbers.

Special handling:

- omitted original-text gaps are tokenized into separators, numbers, currency symbols, URL glue, email glue, or other tokens;
- `+`, `++`, and `#` suffixes produce both suffixed and suffixless forms;
- URL and email groups produce full-value and domain forms when URL/email merging is enabled;
- currency symbols produce symbol, localized names, and ISO code forms when currency classification and expansion are enabled;
- selected exceptional diacritics are removed from fallback word and number forms.

## Example

Input model token:

```text
text=мыла, offsets=[5,9], lemma=мыть
```

Conservative search token:

```text
text=мыла, offsets=[5,9], forms=[мыть], type=WORD
```
