/**
 * Parsed MyStem output models, grammar parsing, offset alignment, and Unicode text preparation.
 *
 * <p>Model collections are immutable. Known token ranges are half-open Java UTF-16 ranges in the
 * caller's original text, even when MyStem processes prepared text. A token's returned surface may
 * differ from that source slice; see {@link io.github.ulviar.mystem4j.model.MystemToken}.
 * This package parses already obtained output and does not launch MyStem or perform network access.
 */
package io.github.ulviar.mystem4j.model;
