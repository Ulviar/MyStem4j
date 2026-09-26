/**
 * Prepares immutable search tokens from parsed MyStem documents.
 *
 * <p>Start with {@link io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer} and its
 * {@link io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer#tokenize tokenize} method.
 * Output tokens partition the complete original text, including separators and fragments omitted by MyStem.
 * Offsets are half-open Java UTF-16 ranges: token text is the corresponding original substring, while
 * lemmas and normalized search forms may have different spelling or length.
 *
 * <p>The default {@link io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions#conservative()
 * conservative preset} recovers gaps and suffixes without semantic entity enrichment. Choose
 * {@link io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions#search() search} for number
 * and currency types or {@link io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions#entityAware()
 * entityAware} for URL/email merging and currency forms. The tokenizer performs no I/O and can be shared
 * between threads; its results and options are immutable.
 */
package io.github.ulviar.mystem4j.tokenization;
