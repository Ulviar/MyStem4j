package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.util.Objects;
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
     * Creates an analyzer with explicit tokenization options and client ownership policy.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param closeClientOnClose whether {@link #close()} should close the supplied client
     */
    public MystemLuceneAnalyzer(
            MystemClient client, MystemSearchTokenizerOptions options, boolean closeClientOnClose) {
        this.client = Objects.requireNonNull(client, "client");
        this.options = Objects.requireNonNull(options, "options");
        this.closeClientOnClose = closeClientOnClose;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MystemLuceneTokenizer(client, options));
    }

    @Override
    public void close() {
        super.close();
        if (closeClientOnClose) {
            client.close();
        }
    }
}
