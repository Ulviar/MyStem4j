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
import java.io.Reader;
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
 * Lucene tokenizer that analyzes the whole input reader with MyStem.
 *
 * <p>The supplied client must produce JSON output. The tokenizer prepares unsafe Unicode input before sending it to
 * MyStem and emits offsets in coordinates of the original Lucene input.
 */
public final class MystemLuceneTokenizer extends Tokenizer {
    private final MystemClient client;
    private final MystemJsonParser parser;
    private final MystemSearchTokenizer searchTokenizer;
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute positionIncrementAttribute =
            addAttribute(PositionIncrementAttribute.class);
    private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
    private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);
    private List<LuceneEmission> emissions = List.of();
    private int emissionIndex;
    private int finalOffset;

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
        this(client, new MystemJsonParser(), new MystemSearchTokenizer(options));
    }

    MystemLuceneTokenizer(MystemClient client, MystemJsonParser parser, MystemSearchTokenizer searchTokenizer) {
        this.client = Objects.requireNonNull(client, "client");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.searchTokenizer = Objects.requireNonNull(searchTokenizer, "searchTokenizer");
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        String originalText = readFully(input);
        finalOffset = correctOffset(originalText.length());
        emissions = analyzeSegments(originalText);
        emissionIndex = 0;
    }

    private List<LuceneEmission> analyzeSegments(String originalText) {
        ArrayList<LuceneEmission> result = new ArrayList<>();
        int segmentStart = 0;
        int index = 0;
        while (index < originalText.length()) {
            char character = originalText.charAt(index);
            if (character == '\r' || character == '\n') {
                appendSegmentEmissions(originalText, segmentStart, index, result);
                index++;
                if (character == '\r' && index < originalText.length() && originalText.charAt(index) == '\n') {
                    index++;
                }
                segmentStart = index;
            } else {
                index++;
            }
        }
        appendSegmentEmissions(originalText, segmentStart, originalText.length(), result);
        return List.copyOf(result);
    }

    private void appendSegmentEmissions(
            String originalText, int startOffset, int endOffset, List<LuceneEmission> result) {
        if (startOffset == endOffset) {
            return;
        }
        String segment = originalText.substring(startOffset, endOffset);
        MystemPreparedText preparedText = MystemTextPreprocessor.prepare(segment);
        MystemRawResult rawResult = client.analyze(preparedText.text());
        MystemDocument document = parser.parse(preparedText, rawResult.output());
        result.addAll(flatten(searchTokenizer.tokenize(document), startOffset));
    }

    @Override
    public boolean incrementToken() {
        if (emissionIndex >= emissions.size()) {
            return false;
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

    @Override
    public void end() throws IOException {
        super.end();
        offsetAttribute.setOffset(finalOffset, finalOffset);
    }

    private static String readFully(Reader reader) throws IOException {
        char[] buffer = new char[4096];
        StringBuilder result = new StringBuilder();
        int read;
        while ((read = reader.read(buffer)) != -1) {
            result.append(buffer, 0, read);
        }
        return result.toString();
    }

    private static List<LuceneEmission> flatten(List<MystemSearchToken> tokens, int offsetShift) {
        ArrayList<LuceneEmission> result = new ArrayList<>();
        for (MystemSearchToken token : tokens) {
            if (!isSearchBearing(token.type())) {
                continue;
            }
            int positionIncrement = 1;
            for (MystemTokenForm form : token.forms()) {
                result.add(new LuceneEmission(
                        form.text(),
                        token.startOffset() + offsetShift,
                        token.endOffset() + offsetShift,
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
