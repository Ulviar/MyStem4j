package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemKnownTextRegressionTest {
    @Test
    void retainsLemmaAssociationsAcrossRepeatedSoftHyphenSeparators() {
        assertNativeFixtures(List.of(new Fixture("😀 Коты\u00AD Собаки\u00AD Мыши", List.of(
                word("Коты\u00AD", true, "кот"),
                word("Собаки\u00AD", true, "собака"),
                word("Мыши", true, "мышь")))));
    }

    @Test
    void composesSoftHyphenAndAccentNormalizationWithEverySupportedSuffix() {
        assertNativeFixtures(List.of(
                new Fixture("😀 Fo\u00ADur Fo\u00ADur+ Fo\u00ADur++ Fo\u00ADur# 11\u00AD22+ 11\u00AD22++ 11\u00AD22#", List.of(
                        word("Fo\u00ADur", false, "four"),
                        word("Fo\u00ADur+", false, "four+", "four"),
                        word("Fo\u00ADur++", false, "four++", "four"),
                        word("Fo\u00ADur#", false, "four#", "four"),
                        group("11\u00AD22+", MystemSearchTokenType.NUMBER, true, "1122+", "1122"),
                        group("11\u00AD22++", MystemSearchTokenType.NUMBER, true, "1122++", "1122"),
                        group("11\u00AD22#", MystemSearchTokenType.NUMBER, true, "1122#", "1122"))),
                new Fixture("😀 Foo\u0301++ 111\u0301++", List.of(
                        word("Foo\u0301++", false, "foo++", "foo"),
                        group("111\u0301++", MystemSearchTokenType.NUMBER, true, "111++", "111")))));
    }

    @Test
    void retainsUnicodeHostsAndEmailPunctuationWithCopiedOrOmittedSeparators() {
        assertNativeFixtures(List.of(
                new Fixture("😀 https://пример.рф", List.of(
                        group("https://пример.рф", MystemSearchTokenType.URL, true, "https://пример.рф", "пример.рф"))),
                new Fixture("😀 https://www.пример.рф/path", List.of(
                        group("https://www.пример.рф/path", MystemSearchTokenType.URL, true,
                                "https://www.пример.рф/path", "пример.рф"))),
                new Fixture("😀 https://www.example.com./path", List.of(
                        group("https://www.example.com./path", MystemSearchTokenType.URL, true,
                                "https://www.example.com./path", "example.com"))),
                new Fixture("😀 o'reilly@example.com,a!b@example.com", List.of(
                        group("o'reilly@example.com", MystemSearchTokenType.EMAIL, true,
                                "o'reilly@example.com", "example.com"),
                        group("a!b@example.com", MystemSearchTokenType.EMAIL, true,
                                "a!b@example.com", "example.com")))));
    }

    @Test
    void keepsOrdinaryLemmasCurrencyNormalizationAndAllFinalTokenSynonyms() {
        assertNativeFixtures(List.of(
                new Fixture("😀 Привет, мир!", List.of(word("Привет", true, "привет"), word("мир", true, "мир"))),
                new Fixture("😀 C++", List.of(word("C++", false, "c++", "c"))),
                new Fixture("😀 $", List.of(group("$", MystemSearchTokenType.CURRENCY, true,
                        "$", "доллар", "dollar", "dólar", "美元", "usd")))));
    }

    private static void assertNativeFixtures(List<Fixture> fixtures) {
        MystemJsonParser parser = new MystemJsonParser();
        MystemSearchTokenizer tokenizer = new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());
        for (boolean copyInput : List.of(false, true)) {
            try (MystemClient client = Mystem.builder()
                    .executable(Path.of(System.getProperty("mystem4j.executable")))
                    .options(MystemOptions.builder()
                            .format(MystemOutputFormat.JSON)
                            .disambiguate(true)
                            .copyInput(copyInput)
                            .build())
                    .session().build()) {
                for (Fixture fixture : fixtures) {
                    String context = fixture.input() + ", copyInput=" + copyInput;
                    List<MystemSearchToken> tokens = tokenizer.tokenize(
                            parser.parse(fixture.input(), client.analyze(fixture.input()).output()));
                    int cursor = 0;
                    for (MystemSearchToken token : tokens) {
                        assertEquals(cursor, token.startOffset(), context);
                        assertEquals(fixture.input().substring(token.startOffset(), token.endOffset()), token.text(), context);
                        cursor = token.endOffset();
                    }
                    assertEquals(fixture.input().length(), cursor, context);

                    List<MystemSearchToken> indexed = tokens.stream()
                            .filter(token -> token.type() != MystemSearchTokenType.SEPARATOR
                                    && token.type() != MystemSearchTokenType.OTHER)
                            .toList();
                    assertEquals(fixture.groups().size(), indexed.size(), context);
                    cursor = 0;
                    for (int index = 0; index < fixture.groups().size(); index++) {
                        Group group = fixture.groups().get(index);
                        MystemSearchToken token = indexed.get(index);
                        int start = fixture.input().indexOf(group.surface(), cursor);
                        assertTrue(start >= cursor, context);
                        int end = start + group.surface().length();
                        assertEquals(group.type(), token.type(), context);
                        assertEquals(group.surface(), token.text(), context);
                        assertEquals(start, token.startOffset(), context);
                        assertEquals(end, token.endOffset(), context);
                        assertEquals(group.forms(), token.forms(), context);
                        cursor = end;
                    }
                }
            }
        }
    }

    private static Group word(String surface, boolean keyword, String... forms) {
        return group(surface, MystemSearchTokenType.WORD, keyword, forms);
    }

    private static Group group(String surface, MystemSearchTokenType type, boolean keyword, String... forms) {
        return new Group(surface, type, List.of(forms).stream().map(form -> new MystemTokenForm(form, keyword)).toList());
    }

    private record Fixture(String input, List<Group> groups) {}

    private record Group(String surface, MystemSearchTokenType type, List<MystemTokenForm> forms) {}
}
