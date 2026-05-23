package io.github.ulviar.mystem4j.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.github.ulviar.mystem4j.MystemInvalidOptionsException;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Parses MyStem JSON output into model objects.
 */
public final class MystemJsonParser {
    private final JsonFactory jsonFactory;

    public MystemJsonParser() {
        this(new JsonFactory());
    }

    public MystemJsonParser(JsonFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory");
    }

    public MystemDocument parse(MystemRawResult result) {
        Objects.requireNonNull(result, "result");
        if (result.format() != MystemOutputFormat.JSON) {
            throw new MystemInvalidOptionsException("MystemJsonParser requires JSON raw results.");
        }
        return parse(result.input(), result.output());
    }

    public MystemDocument parse(String originalText, String json) {
        Objects.requireNonNull(originalText, "originalText");
        Objects.requireNonNull(json, "json");
        try (JsonParser parser = jsonFactory.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new MystemJsonParseException("MyStem JSON root must be an array.");
            }
            MystemOffsetAligner aligner = new MystemOffsetAligner(originalText);
            ArrayList<MystemToken> tokens = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    throw new MystemJsonParseException("MyStem JSON item must be an object.");
                }
                RawToken item = readToken(parser);
                String text = item.text();
                MystemTextRange range = aligner.align(text);
                tokens.add(new MystemToken(text, range.startOffset(), range.endOffset(), item.analyses()));
            }
            if (parser.nextToken() != null) {
                throw new MystemJsonParseException("MyStem JSON output contains data after the root array.");
            }
            return new MystemDocument(originalText, tokens, aligner.issues());
        } catch (IOException error) {
            throw new MystemJsonParseException("Failed to parse MyStem JSON output.", error);
        }
    }

    private static RawToken readToken(JsonParser parser) throws IOException {
        String text = "";
        List<MystemAnalysis> analyses = List.of();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new MystemJsonParseException("MyStem JSON item field name expected.");
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
                throw new MystemJsonParseException("MyStem JSON analysis item must be an object.");
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
                throw new MystemJsonParseException("MyStem JSON analysis field name expected.");
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

    private record RawToken(String text, List<MystemAnalysis> analyses) {}
}
