package io.github.ulviar.mystem4j.tokenization;

/**
 * Classifies source tokens for search pipelines independently of their alternative forms.
 *
 * <p>Number, URL, email, and currency types require their corresponding
 * {@link MystemSearchTokenizerOptions} switches. Separators and other fragments remain in tokenizer
 * results to preserve the complete source partition; the Lucene adapter skips them.
 */
public enum MystemSearchTokenType {
    /** A word with lemma or surface forms. */
    WORD,
    /** A numeric token when number classification is enabled. */
    NUMBER,
    /** A syntactically recognized URL when URL merging is enabled. */
    URL,
    /** A syntactically recognized email address when email merging is enabled. */
    EMAIL,
    /** A recognized currency symbol when currency classification is enabled. */
    CURRENCY,
    /** Whitespace or punctuation classified as a separator. */
    SEPARATOR,
    /** A source fragment that has none of the other classifications. */
    OTHER
}
