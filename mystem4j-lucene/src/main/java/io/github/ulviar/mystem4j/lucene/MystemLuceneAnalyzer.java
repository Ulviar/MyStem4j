package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTermNormalizer;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Analyzes Lucene fields into MyStem lemmas and fallback terms using a JSON client.
 *
 * <p>The analyzer does not close the supplied client unless constructed with {@code closeClientOnClose=true}. For
 * concurrent indexing, use a pooled runtime client. The default analysis options warn for known runtime client profiles
 * that are safe but slow for indexing.
 *
 * <p>Thread safety follows Lucene {@link Analyzer}: one analyzer instance can be reused by Lucene, but the supplied
 * {@link MystemClient} must be suitable for the caller's indexing or query-analysis concurrency.
 * Token streams are per-use resources and must not be shared between threads. Finish active indexing or
 * query analysis before closing the analyzer or its client.
 *
 * <p>The default tokenizer options are {@link MystemSearchTokenizerOptions#conservative()}, and default
 * field/chunk limits are defined by {@link MystemLuceneAnalysisOptions#defaults()}. Use the same settings
 * for index and query-text analysis. Alternative forms share a position and original UTF-16 offsets;
 * {@link MystemLuceneTokenizer} documents emitted attributes and skipped fragments.
 *
 * <p>Single-term normalization uses {@link MystemSearchTermNormalizer} without calling MyStem.
 * It matches normalized aliases emitted during indexing, including for literal URL/email forms.
 * It preserves one term, which can be empty, and preserves wildcard punctuation. It does not lemmatize
 * query patterns or add synonyms. For example, {@code normalize("body", "Fo\u00ADur*")} produces
 * {@code four*}, while normal query analysis is needed to turn Russian word forms into lemmas.
 *
 * <p>To inspect terms with an existing caller-owned JSON client:
 * <pre>{@code
 * try (var analyzer = new MystemLuceneAnalyzer(client);
 *      var stream = analyzer.tokenStream("body", "Мама мыла раму")) {
 *     var term = stream.addAttribute(CharTermAttribute.class);
 *     stream.reset();
 *     while (stream.incrementToken()) {
 *         System.out.println(term.toString());
 *     }
 *     stream.end();
 * }
 * }</pre>
 * The example requires {@link CharTermAttribute} and an enclosing method that handles
 * {@link IOException}. Closing the analyzer in this example leaves {@code client} open.
 */
public final class MystemLuceneAnalyzer extends Analyzer {
    private final MystemClient client;
    private final MystemSearchTokenizerOptions options;
    private final boolean closeClientOnClose;
    private final MystemLuceneAnalysisOptions analysisOptions;
    private final AtomicBoolean closedClient = new AtomicBoolean();

    /**
     * Creates a caller-owned-client analyzer with conservative tokenization and default analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     * @throws NullPointerException if {@code client} or its format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(MystemClient client) {
        this(client, MystemSearchTokenizerOptions.conservative(), false);
    }

    /**
     * Creates a caller-owned-client analyzer with explicit tokenization and default analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     * @throws NullPointerException if an argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options) {
        this(client, options, false);
    }

    /**
     * Creates a caller-owned-client analyzer with a custom field limit and bounded default chunk size.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param maxInputChars positive maximum analyzed field length after character filtering, in UTF-16 code units
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format or the limit is invalid
     * @throws NullPointerException if a reference argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, int maxInputChars) {
        this(client, options, false, MystemLuceneAnalysisOptions.withMaxInputChars(maxInputChars));
    }

    /**
     * Creates a caller-owned-client analyzer with explicit tokenization and analysis policies.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param analysisOptions Lucene-side limits and position policy
     * @throws IllegalArgumentException when the client is incompatible with the selected analysis options
     * @throws NullPointerException if an argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(
            MystemClient client, MystemSearchTokenizerOptions options, MystemLuceneAnalysisOptions analysisOptions) {
        this(client, options, false, analysisOptions);
    }

    /**
     * Creates an analyzer with explicit client ownership and default analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param closeClientOnClose whether {@link #close()} should close the supplied client
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     * @throws NullPointerException if a reference argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(
            MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose) {
        this(client, options, closeClientOnClose, MystemLuceneAnalysisOptions.defaults());
    }

    /**
     * Creates an analyzer with explicit tokenization options, ownership policy, and input size limit.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param closeClientOnClose whether {@link #close()} should close the supplied client
     * @param maxInputChars positive maximum analyzed field length after character filtering, in UTF-16 code units
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format or the limit is invalid
     * @throws NullPointerException if a reference argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(
            MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose, int maxInputChars) {
        this(client, options, closeClientOnClose, MystemLuceneAnalysisOptions.withMaxInputChars(maxInputChars));
    }

    /**
     * Creates an analyzer with explicit tokenization options, ownership policy, and Lucene analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param closeClientOnClose whether {@link #close()} should close the supplied client
     * @param analysisOptions Lucene-side limits and position policy
     * @throws IllegalArgumentException when the client is incompatible with the selected analysis options
     * @throws NullPointerException if a reference argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneAnalyzer(
            MystemClient client,
            MystemSearchTokenizerOptions options,
            boolean closeClientOnClose,
            MystemLuceneAnalysisOptions analysisOptions) {
        this.client = Objects.requireNonNull(client, "client");
        this.options = Objects.requireNonNull(options, "options");
        this.closeClientOnClose = closeClientOnClose;
        this.analysisOptions = Objects.requireNonNull(analysisOptions, "analysisOptions");
        requireJsonOutput(this.client);
        MystemLuceneClientPolicies.apply(this.client, this.analysisOptions.clientPolicy());
    }

    /**
     * Creates tokenizer components using this analyzer's client and policies for every field.
     *
     * @param fieldName field being analyzed; policies do not vary by field
     * @return components containing a new MyStem tokenizer
     */
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MystemLuceneTokenizer(client, options, analysisOptions));
    }

    /**
     * Applies single-term character normalization without making a MyStem request.
     *
     * @param fieldName field being normalized; normalization does not vary by field
     * @param in Lucene's single-term input stream
     * @return a filter preserving token count and applying {@link MystemSearchTermNormalizer}
     */
    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new TokenFilter(in) {
            private final CharTermAttribute term = addAttribute(CharTermAttribute.class);

            @Override
            public boolean incrementToken() throws IOException {
                if (!input.incrementToken()) {
                    return false;
                }
                String normalized = MystemSearchTermNormalizer.normalize(term.toString());
                term.setEmpty().append(normalized);
                return true;
            }
        };
    }

    /**
     * Releases analyzer resources and closes the client only when ownership was explicitly requested.
     *
     * <p>The owned client's {@link MystemClient#close()} method is invoked at most once. Finish active
     * token streams before calling this method; a caller-owned client remains available afterward.
     */
    @Override
    public void close() {
        super.close();
        if (closeClientOnClose && closedClient.compareAndSet(false, true)) {
            client.close();
        }
    }

    private static void requireJsonOutput(MystemClient client) {
        Objects.requireNonNull(client.outputFormat(), "client.outputFormat()").ifPresent(format -> {
            if (format != MystemOutputFormat.JSON) {
                throw new IllegalArgumentException(
                        "MystemLuceneAnalyzer requires a MyStem client configured for JSON output, but the supplied "
                                + "client reports " + format + ".");
            }
        });
    }

}
