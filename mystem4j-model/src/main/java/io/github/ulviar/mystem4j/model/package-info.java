/**
 * Parses MyStem JSON into immutable morphology models with offsets in the caller's original text.
 *
 * <p>Start with {@link io.github.ulviar.mystem4j.model.MystemJsonParser} when you already have MyStem
 * output. Use {@link io.github.ulviar.mystem4j.model.MystemTextPreprocessor} before sending text to
 * MyStem if it can contain unsafe control characters or malformed UTF-16. Pass the resulting
 * {@link io.github.ulviar.mystem4j.model.MystemPreparedText} to the parser so that replacement
 * diagnostics and original offsets are retained. This package does not launch MyStem or access
 * the network.
 *
 * <h2>Recovering the original source</h2>
 *
 * <p>Offsets count Java UTF-16 code units, exactly as {@link java.lang.String#substring(int, int)}
 * does. A known range has an inclusive start and an exclusive end. Both offsets are {@code -1}
 * when alignment fails. The returned token surface can differ from the source: MyStem can omit
 * soft hyphens, and preparation can replace characters.
 *
 * <pre>{@code
 * import io.github.ulviar.mystem4j.model.MystemDocument;
 * import io.github.ulviar.mystem4j.model.MystemJsonParser;
 * import io.github.ulviar.mystem4j.model.MystemToken;
 *
 * MystemDocument document = new MystemJsonParser().parse(
 *         "Мама!", "[{\"text\":\"Мама\",\"analysis\":[{\"lex\":\"мама\",\"gr\":\"S\"}]}]");
 * for (MystemToken token : document.tokens()) {
 *     if (token.hasKnownOffsets()) {
 *         String source = document.originalText().substring(token.startOffset(), token.endOffset());
 *         System.out.println(source);
 *     }
 * }
 * }</pre>
 *
 * <p>Tokens retain MyStem output order; they need not cover the entire input. Inspect
 * {@link io.github.ulviar.mystem4j.model.MystemDocument#issues()} for non-fatal replacement and
 * alignment diagnostics. Invalid JSON instead raises
 * {@link io.github.ulviar.mystem4j.model.MystemJsonParseException}.
 *
 * <h2>Model conventions</h2>
 *
 * <p>Model values and their collections are immutable and can be shared between threads. Lists
 * preserve input order; sets have no specified iteration order. Public model constructors reject
 * {@code null} values and collection elements. They copy supplied collections but do not parse
 * grammar, perform alignment, or check a token range against a containing document's text.
 */
package io.github.ulviar.mystem4j.model;
