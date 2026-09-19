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
 * Lucene analyzer backed by a MyStem JSON client.
 *
 * <p>The analyzer does not close the supplied client unless constructed with {@code closeClientOnClose=true}. For
 * concurrent indexing, use a pooled runtime client. The default analysis options warn for known runtime client profiles
 * that are safe but slow for indexing.
 *
 * <p>Thread safety follows Lucene {@link Analyzer}: one analyzer instance can be reused by Lucene, but the supplied
 * {@link MystemClient} must be suitable for the caller's indexing or query-analysis concurrency.
 *
 * <p>Single-term normalization uses {@link MystemSearchTermNormalizer} without calling MyStem.
 * It matches normalized aliases emitted during indexing, including for literal URL/email forms.
 * It does not lemmatize query patterns or add synonyms.
 */
public final class MystemLuceneAnalyzer extends Analyzer {
    private final MystemClient client;
    private final MystemSearchTokenizerOptions options;
    private final boolean closeClientOnClose;
    private final MystemLuceneAnalysisOptions analysisOptions;
    private final AtomicBoolean closedClient = new AtomicBoolean();

    /**
     * Creates an analyzer with conservative tokenization options.
     *
     * @param client MyStem client configured for JSON output
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     */
    public MystemLuceneAnalyzer(MystemClient client) {
        this(client, MystemSearchTokenizerOptions.conservative(), false);
    }

    /**
     * Creates an analyzer with explicit tokenization options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     */
    public MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options) {
        this(client, options, false);
    }

    /**
     * Creates an analyzer with explicit tokenization options and input size limit.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format or the limit is invalid
     */
    public MystemLuceneAnalyzer(MystemClient client, MystemSearchTokenizerOptions options, int maxInputChars) {
        this(client, options, false, MystemLuceneAnalysisOptions.withMaxInputChars(maxInputChars));
    }

    /**
     * Creates an analyzer with explicit tokenization and Lucene analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param analysisOptions Lucene-side limits and position policy
     * @throws IllegalArgumentException when the client is incompatible with the selected analysis options
     */
    public MystemLuceneAnalyzer(
            MystemClient client, MystemSearchTokenizerOptions options, MystemLuceneAnalysisOptions analysisOptions) {
        this(client, options, false, analysisOptions);
    }

    /**
     * Creates an analyzer with explicit tokenization options and client ownership policy.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param closeClientOnClose whether {@link #close()} should close the supplied client
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
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
     * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format or the limit is invalid
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

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MystemLuceneTokenizer(client, options, analysisOptions));
    }

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
