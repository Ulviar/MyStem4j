package consumer;

import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;

public final class Smoke {
    public static void main(String[] args) {
        var prepared = MystemTextPreprocessor.prepareJsonLine("😀\nМамы");
        var document = new MystemJsonParser().parse(prepared,
                "[{\"text\":\"Мамы\",\"analysis\":[{\"lex\":\"мама\",\"gr\":\"S\"}]}]");
        var token = document.tokens().getFirst();
        if (token.startOffset() != 3 || token.endOffset() != 7
                || !token.analyses().getFirst().lemma().equals("мама")) {
            throw new AssertionError(document);
        }
    }
}
