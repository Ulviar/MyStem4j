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
 * Parses MyStem JSON output into model objects.
 *
 * <p>Input is one or more top-level arrays of token objects, as produced for multiline MyStem output.
 * Token fields {@code text} and {@code analysis}, and analysis fields {@code lex}, {@code gr}, and
 * {@code wt}, may be absent. Absent strings default to empty strings, absent analyses to an empty list,
 * and absent weights to an empty optional. Present known fields must have their expected JSON types;
 * explicit {@code null} is rejected. Unknown fields, including nested values, are ignored.
 *
 * <p>Parsing and left-to-right alignment are separate: unmatched surfaces are retained with unknown
 * offsets and {@link MystemTextIssueType#UNMATCHED_TOKEN} issues rather than causing a parse failure.
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
     * @param originalText text originally sent to MyStem
     * @param json MyStem JSON output
     * @return parsed document with original-text offsets
     * @throws MystemJsonParseException when the JSON is malformed or has an unsupported shape
     * @throws NullPointerException when {@code originalText} or {@code json} is {@code null}
     */
    public MystemDocument parse(String originalText, String json) {
        Objects.requireNonNull(originalText, "originalText");
        return parse(originalText, originalText, IntUnaryOperator.identity(), List.of(), json);
    }

    /**
     * Parses MyStem JSON for preprocessed text and maps token offsets back to the original text.
     *
     * @param preparedText preprocessed text sent to MyStem
     * @param json MyStem JSON output
     * @return parsed document with original-text offsets and preprocessing issues
     * @throws MystemJsonParseException when the JSON is malformed or has an unsupported shape
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
