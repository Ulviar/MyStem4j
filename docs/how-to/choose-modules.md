# Choose Modules

Use only the module that matches the highest-level API you need.

## Runtime Only

Use `mystem4j-runtime` when you only need to run MyStem and receive raw JSON lines.

## Parsed MyStem Output

Use `mystem4j-model` when you already have MyStem JSON and need typed documents, analyses, grammar data, Unicode preparation, and offset alignment.

## Search Tokens Without Lucene

Use `mystem4j-tokenization` when you need search-oriented tokens but do not use Lucene.

## Lucene

Use `mystem4j-lucene` for Lucene analyzers and tokenizers. It brings the runtime, model, and tokenization modules transitively where they are part of the public API.

## Kotlin

Add `mystem4j-kotlin` only when you want the Kotlin DSL and extension helpers.

## Gradle

Use the Gradle plugin in builds that download, cache, extract, or wire the native MyStem binary.
