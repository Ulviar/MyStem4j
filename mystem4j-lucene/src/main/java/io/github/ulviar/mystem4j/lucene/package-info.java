/**
 * Indexes and analyzes queries through Lucene using a MyStem JSON client.
 *
 * <p>Use {@link io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer} for normal indexing and query
 * analysis. A pooled client supports concurrent analysis; the caller retains ownership of the client
 * unless explicitly transferred to the analyzer. Close token streams using Lucene's reset, consume,
 * end, and close lifecycle, and close the analyzer when no analysis remains active.
 *
 * <p>Alternative forms share a position and source offsets. Separators and other non-search fragments
 * are skipped; {@link io.github.ulviar.mystem4j.lucene.MystemLucenePositionPolicy} determines their
 * effect on phrase positions. Offsets remain half-open UTF-16 ranges in the original field, including
 * correction through Lucene character filters. Search terms can differ in length from that source.
 *
 * <p>{@link io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions} bounds fields and requests.
 * Long runs can be split across requests, which can change their morphology. Use the same tokenizer
 * and analysis options for indexing and query text. Single-term query normalization preserves one
 * term and never calls MyStem; it does not replace query analysis for lemmatization.
 */
package io.github.ulviar.mystem4j.lucene;
