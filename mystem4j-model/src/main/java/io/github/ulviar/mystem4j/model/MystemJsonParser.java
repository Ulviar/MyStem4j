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
 */
public final class MystemJsonParser {
    private final JsonFactory jsonFactory;

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
            JsonToken valueToken = parser.nextToken();
            switch (fieldName) {
                case "text" -> text = readStringOrEmpty(parser, valueToken);
                case "analysis" -> analyses = readAnalyses(parser);
                default -> parser.skipChildren();
            }
        }
        return new RawToken(text, analyses);
    }

    private static List<MystemAnalysis> readAnalyses(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }
        ArrayList<MystemAnalysis> analyses = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw parseError(parser, "MyStem JSON analysis item must be an object");
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
                case "lex" -> lemma = readStringOrEmpty(parser, valueToken);
                case "gr" -> grammar = readStringOrEmpty(parser, valueToken);
                case "wt" -> {
                    if (valueToken.isNumeric()) {
                        weight = OptionalDouble.of(parser.getDoubleValue());
                    } else {
                        parser.skipChildren();
                    }
                }
                default -> parser.skipChildren();
            }
        }
        return new MystemAnalysis(lemma, MystemGrammarParser.parse(grammar), weight);
    }

    private static String readStringOrEmpty(JsonParser parser, JsonToken valueToken) throws IOException {
        if (valueToken == JsonToken.VALUE_STRING) {
            return parser.getValueAsString("");
        }
        parser.skipChildren();
        return "";
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
