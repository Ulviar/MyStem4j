package consumer;

import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;
import io.github.ulviar.mystem4j.tokenization.MystemSearchTermNormalizer;

public final class Smoke {
    public static void main(String[] args) {
        var document = new MystemJsonParser().parse("Мамы",
                "[{\"text\":\"Мамы\",\"analysis\":[{\"lex\":\"мама\",\"gr\":\"S\"}]}]");
        var token = new MystemSearchTokenizer().tokenize(document).getFirst();
        if (!token.forms().getFirst().text().equals("мама")
                || token.startOffset() != 0 || token.endOffset() != 4) {
            throw new AssertionError(token);
        }
        if (!MystemSearchTermNormalizer.normalize("Fo\u00ADur*").equals("four*")) {
            throw new AssertionError("Query normalization is unavailable to an isolated tokenization consumer.");
        }
    }
}
