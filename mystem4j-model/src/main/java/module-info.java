/**
 * Immutable MyStem morphology models, JSON and grammar parsing, and UTF-16 text alignment.
 *
 * <p>Use {@link io.github.ulviar.mystem4j.model.MystemJsonParser} to consume existing MyStem JSON
 * output. The module has no dependency on process execution or a search framework; see the
 * {@link io.github.ulviar.mystem4j.model} package for offset and collection contracts.
 */
module io.github.ulviar.mystem4j.model {
    requires com.fasterxml.jackson.core;

    exports io.github.ulviar.mystem4j.model;
}
