package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.Analyzer;

/**
 * Lucene analyzer backed by a MyStem JSON client.
 *
 * <p>The analyzer does not close the supplied client unless constructed with {@code closeClientOnClose=true}. For
 * concurrent indexing, use a thread-safe client mode such as one-shot or pooled runtime clients.
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
     */
    public MystemLuceneAnalyzer(MystemClient client) {
        this(client, MystemSearchTokenizerOptions.conservative(), false);
    }

    /**
     * Creates an analyzer with explicit tokenization options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
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
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MystemLuceneTokenizer(client, options, analysisOptions));
    }

    @Override
    public void close() {
        super.close();
        if (closeClientOnClose && closedClient.compareAndSet(false, true)) {
            client.close();
        }
    }
}
