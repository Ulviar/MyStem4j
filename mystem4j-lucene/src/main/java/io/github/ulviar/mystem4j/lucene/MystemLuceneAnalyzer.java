package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemClientExecutionProfile;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * Lucene analyzer backed by a MyStem JSON client.
 *
 * <p>The analyzer does not close the supplied client unless constructed with {@code closeClientOnClose=true}. For
 * concurrent indexing, use a pooled runtime client. The default analysis options warn for known runtime client profiles
 * that are safe but slow for indexing.
 *
 * <p>Thread safety follows Lucene {@link Analyzer}: one analyzer instance can be reused by Lucene, but the supplied
 * {@link MystemClient} must be suitable for the caller's indexing or query-analysis concurrency.
 */
public final class MystemLuceneAnalyzer extends Analyzer {
    private static final System.Logger LOGGER = System.getLogger(MystemLuceneAnalyzer.class.getName());

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
        applyClientPolicy(this.client, this.analysisOptions.clientPolicy());
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MystemLuceneTokenizer(client, options, analysisOptions));
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    @Override
    public void close() {
        super.close();
        if (closeClientOnClose && closedClient.compareAndSet(false, true)) {
            client.close();
        }
    }

    private static void applyClientPolicy(MystemClient client, MystemLuceneClientPolicy policy) {
        MystemClientExecutionProfile profile =
                Objects.requireNonNull(client.executionProfile(), "client.executionProfile()");
        if (profile == MystemClientExecutionProfile.UNKNOWN
                || profile == MystemClientExecutionProfile.POOLED_SESSIONS
                || policy == MystemLuceneClientPolicy.ALLOW_ANY) {
            return;
        }

        String message = clientPolicyMessage(profile);
        if (policy == MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN) {
            throw new IllegalArgumentException(message);
        }
        LOGGER.log(System.Logger.Level.WARNING, message);
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

    private static String clientPolicyMessage(MystemClientExecutionProfile profile) {
        return switch (profile) {
            case ONE_SHOT_PROCESS_PER_REQUEST ->
                "MystemLuceneAnalyzer received a one-shot MyStem client; indexing will start a native MyStem process "
                        + "per analyzed field. Use a pooled client for indexing or set "
                        + "MystemLuceneClientPolicy.ALLOW_ANY.";
            case REUSABLE_SESSION ->
                "MystemLuceneAnalyzer received a reusable-session MyStem client; concurrent Lucene indexing will "
                        + "serialize requests through one MyStem process. Use a pooled client for indexing or set "
                        + "MystemLuceneClientPolicy.ALLOW_ANY.";
            case UNKNOWN, POOLED_SESSIONS ->
                throw new IllegalArgumentException("No Lucene policy message for " + profile);
        };
    }
}
