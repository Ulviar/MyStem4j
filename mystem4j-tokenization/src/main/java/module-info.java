/**
 * Converts parsed MyStem documents into immutable search tokens with original UTF-16 source ranges.
 *
 * <p>This module exposes the model API transitively and performs no process execution or network access.
 */
module io.github.ulviar.mystem4j.tokenization {
    requires transitive io.github.ulviar.mystem4j.model;

    exports io.github.ulviar.mystem4j.tokenization;
}
