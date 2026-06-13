package io.github.ulviar.mystem4j.tokenization;

import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

final class MystemPreparedSearchToken {
    final String text;
    final List<String> lemmas;
    final ArrayList<String> forms;
    final EnumSet<MystemTokenFeature> features;
    int startOffset;
    int endOffset;

    private MystemPreparedSearchToken(
            String text,
            List<String> lemmas,
            List<String> forms,
            EnumSet<MystemTokenFeature> features,
            int startOffset,
            int endOffset) {
        this.text = text;
        this.lemmas = List.copyOf(lemmas);
        this.forms = new ArrayList<>(forms);
        this.features = features.clone();
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    static MystemPreparedSearchToken from(MystemToken token, MystemSearchTokenizerOptions options) {
        ArrayList<String> lemmas = lemmas(token, options.lemmaSelectionPolicy());
        EnumSet<MystemTokenFeature> features =
                MystemSearchTokenClassifier.detectFeatures(token.text(), lemmas, options);
        return new MystemPreparedSearchToken(
                token.text(), lemmas, List.of(), features, token.startOffset(), token.endOffset());
    }

    private static ArrayList<String> lemmas(MystemToken token, MystemLemmaSelectionPolicy policy) {
        return switch (policy) {
            case ALL -> allLemmas(token);
            case BEST_WEIGHT -> bestWeightedLemma(token);
        };
    }

    private static ArrayList<String> allLemmas(MystemToken token) {
        ArrayList<String> lemmas = new ArrayList<>();
        for (MystemAnalysis analysis : token.analyses()) {
            if (!analysis.lemma().isEmpty() && !lemmas.contains(analysis.lemma())) {
                lemmas.add(analysis.lemma());
            }
        }
        return lemmas;
    }

    private static ArrayList<String> bestWeightedLemma(MystemToken token) {
        Optional<MystemAnalysis> weighted = Optional.empty();
        for (MystemAnalysis analysis : token.analyses()) {
            if (analysis.lemma().isEmpty() || analysis.weight().isEmpty()) {
                continue;
            }
            if (weighted.isEmpty()
                    || analysis.weight().orElseThrow() > weighted.orElseThrow().weight().orElseThrow()) {
                weighted = Optional.of(analysis);
            }
        }
        String lemma = weighted.map(MystemAnalysis::lemma).orElseGet(() -> firstLemma(token));
        ArrayList<String> result = new ArrayList<>();
        if (!lemma.isEmpty()) {
            result.add(lemma);
        }
        return result;
    }

    private static String firstLemma(MystemToken token) {
        for (MystemAnalysis analysis : token.analyses()) {
            if (!analysis.lemma().isEmpty()) {
                return analysis.lemma();
            }
        }
        return "";
    }

    static MystemPreparedSearchToken gap(
            String text, int startOffset, int endOffset, MystemSearchTokenizerOptions options) {
        return new MystemPreparedSearchToken(
                text,
                List.of(),
                List.of(),
                MystemSearchTokenClassifier.detectFeatures(text, List.of(), options),
                startOffset,
                endOffset);
    }

    static MystemPreparedSearchToken composite(
            String text, int startOffset, int endOffset, MystemTokenFeature feature) {
        return new MystemPreparedSearchToken(text, List.of(), List.of(), EnumSet.of(feature), startOffset, endOffset);
    }

    MystemPreparedSearchToken withOffsets(int startOffset, int endOffset) {
        return new MystemPreparedSearchToken(text, lemmas, forms, features, startOffset, endOffset);
    }
}
