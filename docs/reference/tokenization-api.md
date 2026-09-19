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

By default, if MyStem returns a token with `-1` offsets, the tokenizer ignores that
unaligned model token and synthesizes offset-safe tokens from the original text.
Use `MystemUnmatchedTokenPolicy.FAIL` when the application should reject the whole
document instead.

If the parsed MyStem output skips parts of the original text, the tokenizer
synthesizes gap tokens from the original text before URL/email grouping and form
generation.

The output forms a complete, ordered partition of the original Java string:
each token starts where the previous one ends, and its text equals
`originalText.substring(startOffset, endOffset)`. Offsets are half-open UTF-16
ranges; search forms may differ from those source slices.

When constructing `MystemDocument` yourself, supply nonempty source ranges for
nonempty model tokens, ordered without overlaps, and keep their endpoints outside
valid surrogate pairs.
Invalid ranges raise `MystemTokenizationException`. Empty model tokens are ignored;
an unpaired surrogate already present in the source is preserved in source text.

Suffix recovery may consume a prefix of a following copied separator. The
remaining fragment keeps its original position; the tokenizer never moves a
model analysis to a later repeated occurrence.

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

Use the builder for custom combinations:

```java
MystemSearchTokenizerOptions options = MystemSearchTokenizerOptions.builder()
        .classifyNumbers(true)
        .mergeEmails(true)
        .classifyCurrencies(true)
        .lemmaSelectionPolicy(MystemLemmaSelectionPolicy.BEST_WEIGHT)
        .unmatchedTokenPolicy(MystemUnmatchedTokenPolicy.FAIL)
        .build();
```

## Token Model

- `MystemSearchToken` - source text, search forms, Java UTF-16 offsets, and type.
- `MystemTokenForm` - form text plus `keyword` flag.
- `MystemSearchTokenType` - `WORD`, `NUMBER`, `URL`, `EMAIL`, `CURRENCY`, `SEPARATOR`, or `OTHER`.
- `MystemUnmatchedTokenPolicy` - whether unknown-offset model tokens are rejected or recovered from original text.
- `MystemLemmaSelectionPolicy` - whether all MyStem lemmas are emitted or only the highest-weight lemma is used.

`keyword=true` means the form should be treated as already normalized and should not
be changed by later stemming or lowercasing logic. This is useful for values such
as numbers, full URLs, and currency codes.

## Search Forms

The tokenizer emits lemma forms when MyStem analysis has lemmas. By default,
distinct lemmas from all analysis variants are emitted. Set
`MystemLemmaSelectionPolicy.BEST_WEIGHT` to emit only the lemma from the
highest-weight MyStem analysis variant. When no variant has `wt`, that policy uses
the first non-empty lemma.

When a token has no lemma, the tokenizer emits lowercase source forms and marks
them as non-keyword for words and keyword for numbers.

Special handling:

- omitted original-text gaps are tokenized into separators, numbers, currency symbols, URL glue, email glue, or other tokens;
- `+`, `++`, and `#` suffixes produce both suffixed and suffixless forms;
- URL and email groups produce full-value and domain forms when URL/email merging is enabled;
- currency symbols produce symbol, localized names, and lowercase ISO code forms when currency classification and expansion are enabled;
- selected exceptional diacritics are removed from fallback word and number forms.

Word and number forms remove U+00AD SOFT HYPHEN and the exceptional diacritics
U+0301/U+0341 before adding suffixless forms. For example, `Fo\u00ADur` produces
`four`, and `Foo\u0301++` produces both `foo++` and `foo`. Source text and offsets
still include the original characters. Standalone marks remain source tokens.

After the existing forms, the tokenizer adds unique, nonempty aliases using
`MystemSearchTermNormalizer.normalize(String)`. The normalizer removes U+00AD,
U+0301 and U+0341 and applies `Character.toLowerCase` to each remaining code point.
For example, `İstanbul` retains `i\u0307stanbul` and adds `istanbul`; `ΟΣ` retains
`ος` and adds `οσ`. Aliases keep the same keyword flag and source range. Literal
URL/email forms are retained, including their combining marks, with normalized
aliases added only when different.

Custom single-term or prefix/wildcard query code can use the same public
normalizer. It preserves punctuation and wildcard characters, does not invoke
MyStem, and does not lemmatize or expand suffix forms. It can return an empty
string. This is simple Unicode lowercasing, not full or locale-specific case
folding: an existing Greek final sigma remains unchanged. Character normalization
does not replace language-specific query analysis.

Entity merging handles several adjacent entities, including
`a@one.test,b@two.test` and `a@one.test,https://two.test`, preserving their separator
and each entity's full-value/domain forms. Commas and semicolons before another
recognized entity act as boundaries. Otherwise they may remain inside a valid
URL. An email-like value inside URL user info, a path or a query belongs to the URL.

Unicode URL hosts such as `пример.рф` are recognized without DNS requests. The
additional URL domain form removes a leading `www.` and a trailing host dot. In
`https://www.example.com./path`, the full URL form preserves both. A dot at the
very end of a URL is treated as surrounding punctuation. Unquoted email local
parts retain punctuation such as apostrophes and `!`, so `o'reilly@example.com`
stays one address. Paired outer apostrophes and braces remain outside the address.
An invalid domain continuation such as `a@example.com+bad` does not produce the
shorter email `a@example.com`. Malformed host labels, consecutive local-part dots
and ports outside 0–65535 are not
accepted as entities or shortened to a different valid-looking address. Host
recognition is syntactic and does not require a known TLD or a reachable server.

## Example

Input model token:

```text
text=мыла, offsets=[5,9], lemma=мыть
```

Conservative search token:

```text
text=мыла, offsets=[5,9], forms=[мыть], type=WORD
```
