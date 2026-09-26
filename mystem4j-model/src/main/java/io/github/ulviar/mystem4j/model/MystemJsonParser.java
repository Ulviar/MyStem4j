package io.github.ulviar.mystem4j.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.IntUnaryOperator;

/**
 * Parses MyStem JSON output and aligns its tokens to the supplied source text.
 *
 * <p>Input is one or more consecutive top-level JSON arrays of token objects, as produced by MyStem
 * for multiline text. Arrays are concatenated in encounter order, with alignment continuing across
 * them. An empty array is valid; an empty or whitespace-only input is not. This parser consumes
 * existing output and does not run MyStem.
 *
 * <table>
 * <caption>Recognized JSON fields</caption>
 * <thead><tr><th scope="col">Object</th><th scope="col">Field</th>
 *     <th scope="col">Type when present</th><th scope="col">Default when absent</th></tr></thead>
 * <tbody>
 * <tr><td>Token</td><td>{@code text}</td><td>String</td><td>Empty string</td></tr>
 * <tr><td>Token</td><td>{@code analysis}</td><td>Array of objects</td><td>Empty list</td></tr>
 * <tr><td>Analysis</td><td>{@code lex}</td><td>String</td><td>Empty string</td></tr>
 * <tr><td>Analysis</td><td>{@code gr}</td><td>String</td>
 *     <td>{@link MystemGrammarParser#parse(String) Grammar parsed} from an empty string</td></tr>
 * <tr><td>Analysis</td><td>{@code wt}</td><td>Number, including an integer</td><td>Empty optional</td></tr>
 * </tbody>
 * </table>
 *
 * <p>Explicit {@code null} and wrong types are rejected for every recognized field. Empty strings
 * and empty analysis arrays are valid. Unknown fields and their nested values are ignored, but must
 * still be valid JSON. Grammar tags are not checked against a fixed vocabulary; weights are not
 * restricted to a probability range. Token and analysis order are preserved.
 *
 * <h2>Alignment and diagnostics</h2>
 *
 * <p>Alignment searches left to right and tolerates soft hyphens omitted from the MyStem surface.
 * The earliest compatible occurrence wins, even when a later occurrence would match exactly.
 * Missing punctuation or whitespace does not prevent alignment of later tokens; the parser does
 * not synthesize items to cover gaps. An empty surface receives a zero-length range at the current
 * alignment position.
 *
 * <p>An unmatched nonempty surface is retained with both offsets set to {@code -1} and a
 * {@link MystemTextIssueType#UNMATCHED_TOKEN} issue. It does not advance the alignment position or
 * cause a parse failure. Check {@link MystemToken#hasKnownOffsets()} before slicing source text.
 * Invalid JSON or field types instead raise {@link MystemJsonParseException}; no partial document
 * is returned.
 *
 * <p>Instances can be reused and shared between threads. Each call owns its parsing and alignment
 * state and returns an immutable document. For an example, see the
 * {@link io.github.ulviar.mystem4j.model package documentation}.
 */
public final class MystemJsonParser {
    private final JsonFactory jsonFactory;

    /**
     * Creates a parser for the standard MyStem JSON format.
     */
    public MystemJsonParser() {
        this(new JsonFactory());
    }

    MystemJsonParser(JsonFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory");
    }

    /**
     * Parses MyStem JSON and aligns token offsets against the supplied original text.
     *
     * <p>No preprocessing is performed. If MyStem received text from {@link MystemTextPreprocessor},
     * use {@link #parse(MystemPreparedText, String)} to retain the original offsets and replacement
     * issues. The returned document stores {@code originalText} unchanged.
     *
     * @param originalText text supplied to MyStem, also used as the alignment source
     * @param json complete MyStem JSON output containing one or more top-level token arrays
     * @return immutable document with original-text UTF-16 ranges and non-fatal alignment issues
     * @throws MystemJsonParseException if JSON syntax is invalid, a root is not an array, a token
     *     or analysis is not an object, or a known field has an invalid type
     * @throws NullPointerException when {@code originalText} or {@code json} is {@code null}
     */
    public MystemDocument parse(String originalText, String json) {
        Objects.requireNonNull(originalText, "originalText");
        return parse(originalText, originalText, IntUnaryOperator.identity(), List.of(), json);
    }

    /**
     * Parses MyStem JSON for preprocessed text and maps token offsets back to the original text.
     *
     * <p>Alignment uses {@link MystemPreparedText#text()}, then maps both endpoints into
     * {@link MystemPreparedText#originalText()}. The returned token surfaces remain exactly as
     * supplied in JSON. Preparation issues precede alignment issues in the resulting document.
     *
     * @param preparedText the preparation result whose {@link MystemPreparedText#text() text}
     *     was supplied to MyStem
     * @param json complete MyStem JSON output containing one or more top-level token arrays
     * @return immutable document retaining the original text, original-text UTF-16 ranges,
     *     preparation issues, and non-fatal alignment issues
     * @throws MystemJsonParseException if JSON syntax is invalid, a root is not an array, a token
     *     or analysis is not an object, or a known field has an invalid type
     * @throws NullPointerException when {@code preparedText} or {@code json} is {@code null}
     */
    public MystemDocument parse(MystemPreparedText preparedText, String json) {
        Objects.requireNonNull(preparedText, "preparedText");
        return parse(
                preparedText.originalText(),
                preparedText.text(),
                preparedText::originalOffsetFor,
                preparedText.issues(),
                json);
    }

    private MystemDocument parse(
            String originalText,
            String alignmentText,
            IntUnaryOperator originalOffsetFor,
            List<MystemTextIssue> baseIssues,
            String json) {
        Objects.requireNonNull(originalText, "originalText");
        Objects.requireNonNull(alignmentText, "alignmentText");
        Objects.requireNonNull(originalOffsetFor, "originalOffsetFor");
        Objects.requireNonNull(baseIssues, "baseIssues");
        Objects.requireNonNull(json, "json");
        try (JsonParser parser = jsonFactory.createParser(json)) {
            JsonToken rootToken = parser.nextToken();
            if (rootToken != JsonToken.START_ARRAY) {
                throw parseError(parser, "MyStem JSON root must be an array");
            }
            MystemOffsetAligner aligner = new MystemOffsetAligner(alignmentText, originalOffsetFor);
            ArrayList<MystemToken> tokens = new ArrayList<>();
            while (rootToken != null) {
                if (rootToken != JsonToken.START_ARRAY) {
                    throw parseError(parser, "MyStem JSON root values must be arrays");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) {
                        throw parseError(parser, "MyStem JSON item must be an object");
                    }
                    RawToken item = readToken(parser);
                    String text = item.text();
                    MystemTextRange range = aligner.align(text);
                    tokens.add(new MystemToken(text, range.startOffset(), range.endOffset(), item.analyses()));
                }
                rootToken = parser.nextToken();
            }
            ArrayList<MystemTextIssue> issues = new ArrayList<>(baseIssues);
            issues.addAll(aligner.issues());
            return new MystemDocument(originalText, tokens, issues);
        } catch (IOException error) {
            throw parseIoError(error);
        }
    }

    private static RawToken readToken(JsonParser parser) throws IOException {
        String text = "";
        List<MystemAnalysis> analyses = List.of();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw parseError(parser, "MyStem JSON item field name expected");
            }
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "text" -> text = readString(parser, fieldName);
                case "analysis" -> analyses = readAnalyses(parser);
                default -> parser.skipChildren();
            }
        }
        return new RawToken(text, analyses);
    }

    private static List<MystemAnalysis> readAnalyses(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw fieldTypeError(parser, "analysis", "an array");
        }
        ArrayList<MystemAnalysis> analyses = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw parseError(parser, "MyStem JSON 'analysis' item must be an object");
            }
            analyses.add(readAnalysis(parser));
        }
        return List.copyOf(analyses);
    }

    private static MystemAnalysis readAnalysis(JsonParser parser) throws IOException {
        String lemma = "";
        String grammar = "";
        OptionalDouble weight = OptionalDouble.empty();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw parseError(parser, "MyStem JSON analysis field name expected");
            }
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            switch (fieldName) {
                case "lex" -> lemma = readString(parser, fieldName);
                case "gr" -> grammar = readString(parser, fieldName);
                case "wt" -> {
                    if (valueToken != JsonToken.VALUE_NUMBER_INT && valueToken != JsonToken.VALUE_NUMBER_FLOAT) {
                        throw fieldTypeError(parser, fieldName, "a number");
                    }
                    weight = OptionalDouble.of(parser.getDoubleValue());
                }
                default -> parser.skipChildren();
            }
        }
        return new MystemAnalysis(lemma, MystemGrammarParser.parse(grammar), weight);
    }

    private static String readString(JsonParser parser, String fieldName) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw fieldTypeError(parser, fieldName, "a string");
        }
        return parser.getText();
    }

    private static MystemJsonParseException fieldTypeError(JsonParser parser, String fieldName, String expected) {
        return parseError(parser, "MyStem JSON field '" + fieldName + "' must be " + expected
                + " (found " + parser.currentToken() + ")");
    }

    private static MystemJsonParseException parseError(JsonParser parser, String message) {
        return new MystemJsonParseException(message + locationSuffix(parser.currentLocation()) + ".");
    }

    private static MystemJsonParseException parseIoError(IOException error) {
        if (error instanceof JsonProcessingException jsonError) {
            return new MystemJsonParseException(
                    "Failed to parse MyStem JSON output" + locationSuffix(jsonError.getLocation()) + ".",
                    error);
        }
        return new MystemJsonParseException("Failed to parse MyStem JSON output.", error);
    }

    private static String locationSuffix(JsonLocation location) {
        if (location == null || location.getLineNr() < 0 || location.getColumnNr() < 0) {
            return "";
        }
        return " at line " + location.getLineNr() + ", column " + location.getColumnNr();
    }

    private record RawToken(String text, List<MystemAnalysis> analyses) {}
}
