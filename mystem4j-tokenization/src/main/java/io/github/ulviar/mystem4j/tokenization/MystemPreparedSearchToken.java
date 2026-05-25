package io.github.ulviar.mystem4j.tokenization;

import io.github.ulviar.mystem4j.model.MystemAnalysis;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
        ArrayList<String> lemmas = new ArrayList<>();
        for (MystemAnalysis analysis : token.analyses()) {
            if (!analysis.lemma().isEmpty() && !lemmas.contains(analysis.lemma())) {
                lemmas.add(analysis.lemma());
            }
        }
        EnumSet<MystemTokenFeature> features =
                MystemSearchTokenClassifier.detectFeatures(token.text(), lemmas, options);
        return new MystemPreparedSearchToken(
                token.text(), lemmas, List.of(), features, token.startOffset(), token.endOffset());
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
