package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemSearchTokenizerIntegrationTest {
    private final MystemJsonParser parser = new MystemJsonParser();
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void restoresSearchTokensThatRealMystemOmitsWithoutCopyInput() {
        String text = "О\u00ADд\u00ADи\u00ADн Раз++ C++ 10# $ https://example.com me@example.com.";

        try (MystemClient client = Mystem.builder()
                .executable(Path.of(System.getProperty("mystem4j.executable")))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .build())
                .build()) {
            MystemRawResult rawResult = client.analyze(text);
            MystemDocument document = parser.parse(text, rawResult.output());
            List<MystemSearchToken> tokens = tokenizer.tokenize(document);

            assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "О\u00ADд\u00ADи\u00ADн").type());
            assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "Раз++").type());
            assertEquals(MystemSearchTokenType.WORD, tokenByText(tokens, "C++").type());
            assertEquals(MystemSearchTokenType.NUMBER, tokenByText(tokens, "10#").type());
            assertEquals(MystemSearchTokenType.CURRENCY, tokenByText(tokens, "$").type());
            assertEquals(MystemSearchTokenType.URL, tokenByText(tokens, "https://example.com").type());
            assertEquals(MystemSearchTokenType.EMAIL, tokenByText(tokens, "me@example.com").type());
        }
    }

    private static MystemSearchToken tokenByText(List<MystemSearchToken> tokens, String text) {
        return tokens.stream().filter(token -> token.text().equals(text)).findFirst().orElseThrow();
    }
}
