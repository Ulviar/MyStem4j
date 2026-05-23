package io.github.ulviar.mystem4j.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public MystemJsonParser() {
        this(new ObjectMapper());
    }

    public MystemJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new MystemJsonParseException("MyStem JSON root must be an array.");
            }
            MystemOffsetAligner aligner = new MystemOffsetAligner(originalText);
            ArrayList<MystemToken> tokens = new ArrayList<>();
            for (JsonNode item : root) {
                String text = item.path("text").asText("");
                MystemTextRange range = aligner.align(text);
                tokens.add(new MystemToken(text, range.startOffset(), range.endOffset(), analyses(item.path("analysis"))));
            }
            return new MystemDocument(originalText, tokens, aligner.issues());
        } catch (IOException error) {
            throw new MystemJsonParseException("Failed to parse MyStem JSON output.", error);
        }
    }

    private static List<MystemAnalysis> analyses(JsonNode analysisNode) {
        if (!analysisNode.isArray()) {
            return List.of();
        }
        ArrayList<MystemAnalysis> analyses = new ArrayList<>();
        for (JsonNode item : analysisNode) {
            String lemma = item.path("lex").asText("");
            MystemGrammar grammar = MystemGrammarParser.parse(item.path("gr").asText(""));
            OptionalDouble weight = item.has("wt") && item.get("wt").isNumber()
                    ? OptionalDouble.of(item.get("wt").asDouble())
                    : OptionalDouble.empty();
            analyses.add(new MystemAnalysis(lemma, grammar, weight));
        }
        return List.copyOf(analyses);
    }
}
