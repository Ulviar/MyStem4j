# Glossary

## MyStem

Yandex MyStem is a native command-line morphological analyzer for Russian text. It
can return token text, lemmas, grammar tags, and optional weights.

## Lemma

A normalized dictionary form. For example, MyStem can analyze `мыла` with lemma
`мыть`.

## Grammar String

A compact MyStem tag string such as `S,жен,од=им,ед`. MyStem4j keeps the raw string
and also exposes parsed pieces where possible.

## Grammeme

One grammar feature inside a MyStem grammar string, such as gender, case, number,
or tense.

## Part Of Speech

The main grammar category in a MyStem analysis. In `S,жен,од=им,ед`, `S` is the
part of speech.

## Offset

The start or end index of a token in the original Java string. MyStem4j uses Java
UTF-16 offsets, which is also what Lucene expects.

## UTF-16 Code Unit

The index unit used by Java `String`. Most common characters use one code unit.
Some characters outside the Basic Multilingual Plane, such as many emoji, use two.

## Analyzer

A Lucene component that creates token streams for indexing or querying text fields.

## Tokenizer

A Lucene component that reads field text and emits tokens with terms, offsets,
positions, types, and optional flags.

## Token Filter

A Lucene component that modifies a token stream after tokenization. MyStem4j
currently provides an analyzer and tokenizer, not a separate token filter.

## JPMS

The Java Platform Module System. MyStem4j library artifacts include module
descriptors so they can be used on the Java module path.
