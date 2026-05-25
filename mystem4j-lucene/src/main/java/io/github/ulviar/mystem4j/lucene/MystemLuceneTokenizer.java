package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
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

/**
 * Lucene tokenizer that analyzes the input reader with MyStem.
 *
 * <p>The supplied client must produce JSON output. The tokenizer prepares unsafe Unicode input before sending it to
 * MyStem and emits offsets in coordinates of the original Lucene input. Input is processed in bounded chunks to avoid
 * materializing large Lucene fields in memory.
 */
public final class MystemLuceneTokenizer extends Tokenizer {
    public static final int DEFAULT_MAX_INPUT_CHARS = MystemLuceneAnalysisOptions.DEFAULT_MAX_INPUT_CHARS;
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
     * Creates a tokenizer with conservative tokenization options.
     *
     * @param client MyStem client configured for JSON output
     */
    public MystemLuceneTokenizer(MystemClient client) {
        this(client, MystemSearchTokenizerOptions.conservative());
    }

    /**
     * Creates a tokenizer with explicit tokenization options.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     */
    public MystemLuceneTokenizer(MystemClient client, MystemSearchTokenizerOptions options) {
        this(client, options, DEFAULT_MAX_INPUT_CHARS);
    }

    /**
     * Creates a tokenizer with explicit tokenization options and input size limit.
     *
     * @param client MyStem client configured for JSON output
     * @param options search tokenization policy
     * @param maxInputChars maximum number of UTF-16 code units read from one Lucene field
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
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        clearState(false);
    }

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

    private List<LuceneEmission> analyze(String originalText, int offsetShift) {
        if (originalText.isEmpty()) {
            return List.of();
        }
        MystemPreparedText preparedText = MystemTextPreprocessor.prepareJsonLine(originalText);
        MystemRawResult rawResult = client.analyze(preparedText.text());
        MystemDocument document = parser.parse(preparedText, rawResult.output());
        return flatten(searchTokenizer.tokenize(document), offsetShift);
    }

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
        while (!inputExhausted && pendingInput.length() < analysisOptions.maxChunkChars()) {
            readMore();
        }
        if (pendingInput.isEmpty()) {
            finalOffset = correctOffset(totalCharsRead);
            return false;
        }

        int chunkEnd = inputExhausted
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
        if (totalCharsRead + read > analysisOptions.maxInputChars()) {
            throw new IOException(
                    "Lucene field exceeds MyStem tokenizer maxInputChars: " + analysisOptions.maxInputChars());
        }
        pendingInput.append(buffer, 0, read);
        totalCharsRead += read;
    }

    @Override
    public void end() throws IOException {
        super.end();
        offsetAttribute.setOffset(finalOffset, finalOffset);
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
            return limit == 1 ? limit : limit - 1;
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
            pendingPositionIncrement = 1;
            for (MystemTokenForm form : token.forms()) {
                result.add(new LuceneEmission(
                        form.text(),
                        offsetShift + token.startOffset(),
                        offsetShift + token.endOffset(),
                        typeName(token.type()),
                        form.keyword(),
                        positionIncrement));
                positionIncrement = 0;
            }
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
