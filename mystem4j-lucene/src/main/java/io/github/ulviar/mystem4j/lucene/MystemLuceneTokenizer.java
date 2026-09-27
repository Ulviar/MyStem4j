package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemPreparedText;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;
import io.github.ulviar.mystem4j.tokenization.MystemSearchToken;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenType;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizerOptions;
import io.github.ulviar.mystem4j.tokenization.MystemTokenForm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.util.UnicodeUtil;

/**
 * Streams MyStem search forms from a Lucene field in bounded requests.
 *
 * <p>The supplied client must produce MyStem JSON; a declared non-JSON format is rejected at
 * construction, while an unknown declaration is accepted. Before each request, unsafe Unicode is
 * prepared and CR/LF are replaced with spaces. Emitted offsets map back through that preparation,
 * chunk boundaries, and any Lucene character filters to the original field's half-open UTF-16 ranges.
 * Term length must not be used to derive source offsets.
 *
 * <p>{@link CharTermAttribute} contains one search form; {@link KeywordAttribute} carries its keyword
 * flag, and {@link TypeAttribute} contains the lowercase token-type name. All forms of a source token
 * share offsets. The first form advances the position according to
 * {@link MystemLucenePositionPolicy}; additional forms have position increment zero. Separator and
 * other non-search tokens are skipped. Their effect on positions is controlled by the same policy.
 * Forms exceeding {@link IndexWriter#MAX_TERM_LENGTH} UTF-8 bytes are omitted without truncation;
 * safe alternatives retain their offsets and position. A token with no remaining forms occupies one
 * position under either policy, including trailing positions reported by {@link #end()}.
 *
 * <p>Chunk limits count UTF-16 units after character filtering and before Unicode preparation.
 * Boundaries prefer whitespace and never split valid surrogate pairs, even across reader calls. A
 * long run without whitespace can span multiple requests and receive different morphology. A chunk
 * limit of one permits a two-unit surrogate pair when the field limit allows it.
 *
 * <p>Use {@link MystemLuceneAnalyzer} unless custom analyzer wiring is needed. For direct use, follow
 * the Lucene lifecycle: set a reader, {@link #reset()}, consume {@link #incrementToken()} until false,
 * call {@link #end()}, then {@link #close()}. The tokenizer is not thread-safe and never owns or closes
 * its {@link MystemClient}. Closing releases the reader and buffered field data. After complete
 * consumption, final offsets cover the full original field even when only a prefix was indexed.
 */
public final class MystemLuceneTokenizer extends Tokenizer {
    /** Default analyzed field limit, in UTF-16 units after character filtering. */
    public static final int DEFAULT_MAX_INPUT_CHARS = MystemLuceneAnalysisOptions.DEFAULT_MAX_INPUT_CHARS;
    /** Default request chunk limit, in UTF-16 units before MyStem Unicode preparation. */
    public static final int DEFAULT_MAX_CHUNK_CHARS = MystemLuceneAnalysisOptions.DEFAULT_MAX_CHUNK_CHARS;
    private static final int READ_BUFFER_CHARS = 4096;

    private final MystemClient client;
    private final MystemJsonParser parser;
    private final MystemSearchTokenizer searchTokenizer;
    private final MystemLuceneAnalysisOptions analysisOptions;
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute positionIncrementAttribute =
            addAttribute(PositionIncrementAttribute.class);
    private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
    private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);
    private final StringBuilder pendingInput = new StringBuilder();
    private List<LuceneEmission> emissions = List.of();
    private int emissionIndex;
    private int pendingStartOffset;
    private int totalCharsRead;
    private boolean inputExhausted;
    private int finalOffset;
    private int pendingPositionIncrement = 1;

    /**
     * Creates a tokenizer with conservative tokenization and default analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     * @throws NullPointerException if {@code client} or its format/profile declaration is {@code null}
     */
    public MystemLuceneTokenizer(MystemClient client) {
        this(client, MystemSearchTokenizerOptions.conservative());
    }

    /**
     * Creates a tokenizer with explicit tokenization and default analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format
     * @throws NullPointerException if an argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options) {
        this(client, options, DEFAULT_MAX_INPUT_CHARS);
    }

    /**
     * Creates a tokenizer with a custom field limit and bounded default chunk size.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param maxInputChars positive maximum analyzed field length after character filtering, in UTF-16 code units
     * @throws IllegalArgumentException when the client exposes a known non-JSON output format or the limit is invalid
     * @throws NullPointerException if a reference argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options, int maxInputChars) {
        this(client, options, MystemLuceneAnalysisOptions.withMaxInputChars(maxInputChars));
    }

    /**
     * Creates a tokenizer with explicit tokenization and Lucene analysis options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param analysisOptions Lucene-side limits and position policy
     * @throws IllegalArgumentException if the client's output format or execution profile violates the selected policies
     * @throws NullPointerException if an argument or the client's format/profile declaration is {@code null}
     */
    public MystemLuceneTokenizer(
            MystemClient client, MystemSearchTokenizerOptions options, MystemLuceneAnalysisOptions analysisOptions) {
        this(client, new MystemJsonParser(), new MystemSearchTokenizer(options), analysisOptions);
    }

    MystemLuceneTokenizer(
            MystemClient client,
            MystemJsonParser parser,
            MystemSearchTokenizer searchTokenizer,
            MystemLuceneAnalysisOptions analysisOptions) {
        this.client = Objects.requireNonNull(client, "client");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.searchTokenizer = Objects.requireNonNull(searchTokenizer, "searchTokenizer");
        this.analysisOptions = Objects.requireNonNull(analysisOptions, "analysisOptions");
        requireJsonOutput(this.client);
        MystemLuceneClientPolicies.apply(this.client, this.analysisOptions.clientPolicy());
    }

    /**
     * Starts analysis of the reader previously supplied through {@link #setReader(java.io.Reader)}.
     *
     * <p>Clears per-field offsets, positions, and buffered tokens. MyStem requests are deferred until
     * {@link #incrementToken()}.
     *
     * @throws IOException if Lucene cannot reset the input
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        clearState(false);
    }

    /**
     * Closes the current reader and releases buffered field data, leaving the client open.
     *
     * @throws IOException if the reader cannot be closed
     */
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            clearState(true);
        }
    }

    private void clearState(boolean releaseBuffers) {
        pendingInput.setLength(0);
        if (releaseBuffers) {
            pendingInput.trimToSize();
        }
        emissions = List.of();
        emissionIndex = 0;
        pendingStartOffset = 0;
        totalCharsRead = 0;
        inputExhausted = false;
        finalOffset = 0;
        pendingPositionIncrement = 1;
    }

    private static void requireJsonOutput(MystemClient client) {
        Objects.requireNonNull(client.outputFormat(), "client.outputFormat()").ifPresent(format -> {
            if (format != MystemOutputFormat.JSON) {
                throw new IllegalArgumentException(
                        "MystemLuceneTokenizer requires a MyStem client configured for JSON output, but the supplied "
                                + "client reports " + format + ".");
            }
        });
    }

    private List<LuceneEmission> analyze(String originalText, int offsetShift) {
        if (originalText.isEmpty()) {
            return List.of();
        }
        MystemPreparedText preparedText = MystemTextPreprocessor.prepareJsonLine(originalText);
        MystemRawResult rawResult = client.analyze(preparedText.text());
        MystemDocument document = parser.parse(preparedText, rawResult.output());
        return flatten(searchTokenizer.tokenize(document), offsetShift);
    }

    /**
     * Reads and analyzes further chunks as needed, then emits the next term and its Lucene attributes.
     *
     * <p>Read attributes before advancing again: Lucene reuses their mutable instances. After false is
     * returned, call {@link #end()} to obtain final offsets and any trailing skipped-token positions.
     *
     * @return {@code true} when attributes contain a term, or {@code false} at end of input
     * @throws IOException if reading fails or the field limit is exceeded under
     *         {@link MystemLuceneOversizedInputPolicy#FAIL}
     * @throws io.github.ulviar.mystem4j.MystemException if the client cannot complete a request
     * @throws io.github.ulviar.mystem4j.model.MystemJsonParseException if the client returns invalid MyStem JSON
     * @throws io.github.ulviar.mystem4j.tokenization.MystemTokenizationException if parsed tokens cannot be aligned safely
     */
    @Override
    public boolean incrementToken() throws IOException {
        while (emissionIndex >= emissions.size()) {
            if (!loadNextChunk()) {
                return false;
            }
        }
        clearAttributes();
        LuceneEmission emission = emissions.get(emissionIndex++);
        termAttribute.append(emission.term());
        offsetAttribute.setOffset(correctOffset(emission.startOffset()), correctOffset(emission.endOffset()));
        positionIncrementAttribute.setPositionIncrement(emission.positionIncrement());
        typeAttribute.setType(emission.type());
        keywordAttribute.setKeyword(emission.keyword());
        return true;
    }

    private boolean loadNextChunk() throws IOException {
        // One UTF-16 code unit of lookahead makes boundaries independent of Reader fragmentation,
        // and keeps a high surrogate pending until its possible low surrogate has been read.
        while (!inputExhausted && pendingInput.length() <= analysisOptions.maxChunkChars()) {
            readMore();
        }
        if (pendingInput.isEmpty()) {
            finalOffset = correctOffset(totalCharsRead);
            return false;
        }

        int chunkEnd = inputExhausted && pendingInput.length() <= analysisOptions.maxChunkChars()
                ? pendingInput.length()
                : chooseChunkEnd(pendingInput, analysisOptions.maxChunkChars());
        String chunk = pendingInput.substring(0, chunkEnd);
        int chunkStartOffset = pendingStartOffset;
        pendingInput.delete(0, chunkEnd);
        pendingStartOffset += chunkEnd;
        finalOffset = correctOffset(totalCharsRead);
        emissions = analyze(chunk, chunkStartOffset);
        emissionIndex = 0;
        return true;
    }

    private void readMore() throws IOException {
        char[] buffer = new char[READ_BUFFER_CHARS];
        int read = input.read(buffer);
        if (read == -1) {
            inputExhausted = true;
            return;
        }
        if (read > analysisOptions.maxInputChars() - totalCharsRead) {
            if (analysisOptions.oversizedInputPolicy() == MystemLuceneOversizedInputPolicy.FAIL) {
                throw new IOException(
                        "Lucene field exceeds MyStem tokenizer maxInputChars: " + analysisOptions.maxInputChars());
            }
            int allowed = analysisOptions.maxInputChars() - totalCharsRead;
            pendingInput.append(buffer, 0, allowed);
            // The retained high surrogate may belong to an earlier Reader.read call.
            if (!pendingInput.isEmpty()
                    && Character.isHighSurrogate(pendingInput.charAt(pendingInput.length() - 1))
                    && Character.isLowSurrogate(buffer[allowed])) {
                pendingInput.setLength(pendingInput.length() - 1);
            }
            totalCharsRead += read;
            drainRemainingInput(buffer);
            inputExhausted = true;
            return;
        }
        pendingInput.append(buffer, 0, read);
        totalCharsRead += read;
    }

    private void drainRemainingInput(char[] buffer) throws IOException {
        int read;
        while ((read = input.read(buffer)) != -1) {
            totalCharsRead += read;
        }
    }

    /**
     * Sets both final offsets to the corrected end of the complete field after stream exhaustion.
     *
     * <p>With truncation enabled, the unindexed remainder is still read so this offset includes it.
     * The final position increment includes trailing search tokens whose forms exceed Lucene's byte
     * limit, plus trailing non-search fragments under the preserve-skipped policy.
     * This method does not consume unread input; call it after {@link #incrementToken()} returns false.
     *
     * @throws IOException if Lucene cannot finalize the stream
     */
    @Override
    public void end() throws IOException {
        super.end();
        offsetAttribute.setOffset(finalOffset, finalOffset);
        positionIncrementAttribute.setPositionIncrement(Math.max(0, pendingPositionIncrement - 1));
    }

    private static int chooseChunkEnd(CharSequence input, int maxChunkChars) {
        int limit = codePointBoundary(input, Math.min(maxChunkChars, input.length()));
        for (int index = limit; index > 0; ) {
            int codePoint = Character.codePointBefore(input, index);
            if (isPreferredSplitAfter(codePoint)) {
                return index;
            }
            index -= Character.charCount(codePoint);
        }
        return limit;
    }

    private static int codePointBoundary(CharSequence input, int limit) {
        if (limit <= 0) {
            return 0;
        }
        if (limit < input.length()
                && Character.isLowSurrogate(input.charAt(limit))
                && Character.isHighSurrogate(input.charAt(limit - 1))) {
            return limit == 1 ? limit + 1 : limit - 1;
        }
        return limit;
    }

    private static boolean isPreferredSplitAfter(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                || Character.getType(codePoint) == Character.LINE_SEPARATOR
                || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
    }

    private List<LuceneEmission> flatten(List<MystemSearchToken> tokens, int offsetShift) {
        ArrayList<LuceneEmission> result = new ArrayList<>();
        for (MystemSearchToken token : tokens) {
            if (!isSearchBearing(token.type())) {
                if (analysisOptions.positionPolicy() == MystemLucenePositionPolicy.PRESERVE_SKIPPED_TOKENS) {
                    pendingPositionIncrement++;
                }
                continue;
            }
            int positionIncrement = pendingPositionIncrement;
            for (MystemTokenForm form : token.forms()) {
                if (UnicodeUtil.calcUTF16toUTF8Length(form.text(), 0, form.text().length()) > IndexWriter.MAX_TERM_LENGTH) {
                    continue;
                }
                result.add(new LuceneEmission(
                        form.text(),
                        offsetShift + token.startOffset(),
                        offsetShift + token.endOffset(),
                        typeName(token.type()),
                        form.keyword(),
                        positionIncrement));
                positionIncrement = 0;
            }
            // An omitted logical word still occupies a position, even in COMPACT mode.
            pendingPositionIncrement = positionIncrement == 0 ? 1 : pendingPositionIncrement + 1;
        }
        return List.copyOf(result);
    }

    private static boolean isSearchBearing(MystemSearchTokenType type) {
        return type != MystemSearchTokenType.SEPARATOR && type != MystemSearchTokenType.OTHER;
    }

    private static String typeName(MystemSearchTokenType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private record LuceneEmission(
            String term, int startOffset, int endOffset, String type, boolean keyword, int positionIncrement) {}
}
