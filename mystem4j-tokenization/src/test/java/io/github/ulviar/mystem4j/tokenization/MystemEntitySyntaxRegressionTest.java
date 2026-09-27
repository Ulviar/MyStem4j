package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MystemEntitySyntaxRegressionTest {
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void preservesCompleteEntitiesInCapturedMyStemOutput() {
        // MyStem 3.1 macOS x64, -cd --format=json; captures include its appended newline.
        List<CapturedEntity> cases = capturedEntities();
        for (CapturedEntity testCase : cases) {
            MystemDocument document = new MystemJsonParser().parse(testCase.source(), testCase.json());
            assertEntity(document, testCase.source().contains("://") ? MystemSearchTokenType.URL
                    : MystemSearchTokenType.EMAIL, testCase.entityText(), testCase.domain());
        }
    }

    @Test
    void recognizesUnicodeHostsWithoutTruncatingTheAuthority() {
        for (String source : List.of("https://пример.рф", "https://пример.рф/путь", "https://bücher.de/path")) {
            String host = source.substring(8).split("/", 2)[0];
            assertEntity(gaps(source), MystemSearchTokenType.URL, source, host);
        }
    }

    @Test
    void normalizesOnlyTheLeadingWwwLabelAndTheFinalHostDot() {
        for (String[] testCase : List.of(
                new String[]{"https://WWW.Example.COM./path", "example.com"},
                new String[]{"https://www.пример.рф./путь", "пример.рф"},
                new String[]{"https://preview.www.example.com/path", "preview.www.example.com"},
                new String[]{"https://wwwexample.com/path", "wwwexample.com"})) {
            assertEntity(gaps(testCase[0]), MystemSearchTokenType.URL, testCase[0], testCase[1]);
        }
    }

    @Test
    void acceptsSyntacticHostsWithoutATldWhitelist() {
        for (String[] testCase : List.of(
                new String[]{"custom://localhost:65535/path", "localhost"},
                new String[]{"https://service.internal-future/path", "service.internal-future"},
                new String[]{"https://example.com:000443/path", "example.com"},
                new String[]{"https://127.0.0.1:0/path", "127.0.0.1"})) {
            assertEntity(gaps(testCase[0]), MystemSearchTokenType.URL, testCase[0], testCase[1]);
        }
    }

    @Test
    void preservesUrlPathAndQueryTerminators() {
        for (String source : List.of("https://пример.рф/", "https://пример.рф/?", "https://пример.рф/#",
                "https://пример.рф/?q=", "https://пример.рф/?q=a&")) {
            assertEntity(gaps(source), MystemSearchTokenType.URL, source, "пример.рф");
        }
    }

    @Test
    void preservesUrlBracketsSchemesAndTerminalUnreservedCharacters() {
        for (String url : List.of("https://example.com/a(b)c", "https://example.com/a(b)",
                "https://[::1]/path", "https://[::1]", "https://example.com/a~",
                "http+custom://example.com/path", "http-custom.v1://example.com/path")) {
            String domain = url.contains("[::1]") ? "[::1]" : "example.com";
            for (String source : List.of(url, "😀 (" + url + "). after", "[" + url + "]")) {
                assertEntity(gaps(source), MystemSearchTokenType.URL, url, domain);
            }
        }
    }

    @Test
    void preservesUrlBracketsWithCopiedAndOmittedMyStemPunctuation() {
        String source = "https://example.com/a(b)c";
        // MyStem 3.1 macOS x64, -cd / -d --format=json, respectively.
        for (String json : List.of(
                """
                [{"analysis":[],"text":"https"},{"text":"://"},{"analysis":[],"text":"example"},
                 {"text":"."},{"analysis":[],"text":"com"},{"text":"/"},{"analysis":[],"text":"a"},
                 {"text":"("},{"analysis":[],"text":"b"},{"text":")"},{"analysis":[],"text":"c"},
                 {"text":"\\n"}]
                """,
                """
                [{"analysis":[],"text":"https"},{"analysis":[],"text":"example"},
                 {"analysis":[],"text":"com"},{"analysis":[],"text":"a"},
                 {"analysis":[],"text":"b"},{"analysis":[],"text":"c"}]
                """)) {
            assertEntity(new MystemJsonParser().parse(source, json), MystemSearchTokenType.URL,
                    source, "example.com");
        }
    }

    @Test
    void recognizesUrlsInsideCombinedCopiedPunctuationTokens() {
        // MyStem 3.1 macOS x64, -cd --format=json.
        assertEntity(new MystemJsonParser().parse("https://[::1]/path", """
                [{"analysis":[],"text":"https"},{"text":"://[::"},{"text":"1"},{"text":"]/"},
                 {"analysis":[],"text":"path"},{"text":"\\n"}]
                """), MystemSearchTokenType.URL, "https://[::1]/path", "[::1]");
        assertEntity(new MystemJsonParser().parse("(https://example.com/a(b)).", """
                [{"text":"("},{"analysis":[],"text":"https"},{"text":"://"},
                 {"analysis":[],"text":"example"},{"text":"."},{"analysis":[],"text":"com"},
                 {"text":"/"},{"analysis":[],"text":"a"},{"text":"("},
                 {"analysis":[],"text":"b"},{"text":"))"},{"text":"."},{"text":"\\n"}]
                """), MystemSearchTokenType.URL, "https://example.com/a(b)", "example.com");
    }

    @Test
    void preservesAllUnquotedEmailLocalPartPunctuation() {
        for (char punctuation : "!#$%&'*+-/=?^_`{|}~".toCharArray()) {
            String source = "a" + punctuation + "b@example.com";
            assertEntity(gaps(source), MystemSearchTokenType.EMAIL, source, "example.com");
        }
        assertEntity(gaps("first.last+tag@пример.рф"), MystemSearchTokenType.EMAIL,
                "first.last+tag@пример.рф", "пример.рф");
    }

    @Test
    void rejectsMalformedEmailCandidatesWithoutRecognizingASubstring() {
        for (String source : List.of("a..b@example.com", ".a@example.com", "a.@example.com",
                "a@b@example.com", "invalid@@example.com", "a\\b@example.com",
                "a@example..com", "a@-example.com", "a@example-.com")) {
            assertNoEntities(source);
        }
    }

    @Test
    void keepsPairedEmailWrappersOutsideTheAddress() {
        for (String[] testCase : List.of(
                new String[]{"'o'reilly@example.com'", "o'reilly@example.com"},
                new String[]{"{a@example.com}", "a@example.com"},
                new String[]{"'{o'reilly@example.com}'", "o'reilly@example.com"},
                new String[]{"'name@example.com", "'name@example.com"},
                new String[]{"{name@example.com", "{name@example.com"},
                new String[]{"a{b@example.com", "a{b@example.com"})) {
            assertEntity(gaps(testCase[0]), MystemSearchTokenType.EMAIL, testCase[1], "example.com");
        }
    }

    @Test
    void rejectsMalformedEmailHostContinuationsWithoutRecognizingAPrefix() {
        for (String source : List.of("a@example.com+bad", "a@example.com!bad", "a@example.com\\b",
                "a@example.com_bad", "a@example.com/bad", "a@example.com:123", "a@example.com'bad")) {
            assertNoEntities(source);
        }
        for (String source : List.of("a@example.com!", "a@example.com?", "a@example.com:",
                "a@example.com...", "a@example.com'}")) {
            assertEntity(gaps(source), MystemSearchTokenType.EMAIL, "a@example.com", "example.com");
        }
    }

    @Test
    void rejectsInvalidAuthoritiesWithoutFallingBackToShorterUrls() {
        for (String source : List.of("https://example.com:99999/path", "https://example.com:65536",
                "https://example.com:-1/path", "https://example.com:abc/path",
                "https://example.com:/path", "https://www..example.com/path",
                "https://www.-example.com/path", "https://www._example.com/path",
                "https://www.+example.com/path", "https://www!example.com/path", "https://./path",
                "https://[::1]:65536/path", "https://[not-ip]/path")) {
            assertNoEntities(source);
        }
    }

    @Test
    void respectsOptInFlagsAndKeepsAdjacentEntitiesSeparate() {
        String source = "😀[o'reilly@example.com,https://www.пример.рф/path]";
        List<MystemSearchToken> tokens = tokenizer.tokenize(gaps(source));
        assertEquals(List.of("o'reilly@example.com", "https://www.пример.рф/path"),
                tokens.stream().filter(MystemEntitySyntaxRegressionTest::isEntity)
                        .map(MystemSearchToken::text).toList());
        assertPartition(source, tokens);
        assertTrue(new MystemSearchTokenizer().tokenize(gaps(source)).stream().noneMatch(
                MystemEntitySyntaxRegressionTest::isEntity));
        for (MystemSearchTokenizerOptions options : List.of(
                MystemSearchTokenizerOptions.builder().mergeEmails(true).build(),
                MystemSearchTokenizerOptions.builder().mergeUrls(true).build())) {
            List<MystemSearchToken> selected = new MystemSearchTokenizer(options).tokenize(gaps(source));
            assertEquals(1L, selected.stream().filter(MystemEntitySyntaxRegressionTest::isEntity).count());
            assertPartition(source, selected);
        }
    }

    @Test
    void letsAUrlOwnEmailLikeUserInfoPathsAndQueries() {
        for (String source : List.of("https://o'reilly@example.com/path", "https://пример.рф/a!b@example.com",
                "https://пример.рф/?email=o'reilly@example.com")) {
            String domain = source.startsWith("https://o'") ? "example.com" : "пример.рф";
            assertEntity(gaps(source), MystemSearchTokenType.URL, source, domain);
        }
    }

    private void assertEntity(MystemDocument document, MystemSearchTokenType type, String text, String domain) {
        List<MystemSearchToken> tokens = tokenizer.tokenize(document);
        List<MystemSearchToken> entities = tokens.stream().filter(MystemEntitySyntaxRegressionTest::isEntity).toList();
        assertEquals(1, entities.size(), () -> document.originalText() + ": " + tokens);
        MystemSearchToken entity = entities.getFirst();
        assertEquals(type, entity.type(), document.originalText());
        assertEquals(text, entity.text(), document.originalText());
        assertEquals(List.of(new MystemTokenForm(text.toLowerCase(Locale.ROOT), true),
                new MystemTokenForm(domain, true)), entity.forms(), document.originalText());
        assertPartition(document.originalText(), tokens);
    }

    private void assertNoEntities(String source) {
        List<MystemSearchToken> tokens = tokenizer.tokenize(gaps(source));
        assertTrue(tokens.stream().noneMatch(MystemEntitySyntaxRegressionTest::isEntity), () -> source + ": " + tokens);
        assertPartition(source, tokens);
    }

    private static boolean isEntity(MystemSearchToken token) {
        return token.type() == MystemSearchTokenType.URL || token.type() == MystemSearchTokenType.EMAIL;
    }

    private static MystemDocument gaps(String source) {
        return new MystemDocument(source, List.of(), List.of());
    }

    private static void assertPartition(String source, List<MystemSearchToken> tokens) {
        int cursor = 0;
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset(), source);
            assertTrue(token.endOffset() > token.startOffset(), source);
            assertEquals(source.substring(token.startOffset(), token.endOffset()), token.text(), source);
            cursor = token.endOffset();
        }
        assertEquals(source.length(), cursor, source);
    }

    private static List<CapturedEntity> capturedEntities() {
        return List.of(
                new CapturedEntity("https://www.example.com/path", "[{\"analysis\":[],\"text\":\"https\"},{\"text\":\"://\"},{\"analysis\":[],\"text\":\"www\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"/\"},{\"analysis\":[],\"text\":\"path\"},{\"text\":\"\\n\"}]", "example.com"),
                new CapturedEntity("o'reilly@example.com", "[{\"analysis\":[],\"text\":\"o\"},{\"text\":\"'\"},{\"analysis\":[],\"text\":\"reilly\"},{\"text\":\"@\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"\\n\"}]", "example.com"),
                new CapturedEntity("a!b@example.com", "[{\"analysis\":[],\"text\":\"a\"},{\"text\":\"!\"},{\"analysis\":[],\"text\":\"b\"},{\"text\":\"@\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"\\n\"}]", "example.com"),
                new CapturedEntity("https://пример.рф", "[{\"analysis\":[],\"text\":\"https\"},{\"text\":\"://\"},{\"analysis\":[{\"lex\":\"пример\"}],\"text\":\"пример\"},{\"text\":\".\"},{\"analysis\":[{\"lex\":\"рф\"}],\"text\":\"рф\"},{\"text\":\"\\n\"}]", "пример.рф"),
                new CapturedEntity("https://www.пример.рф/path", "[{\"analysis\":[],\"text\":\"https\"},{\"text\":\"://\"},{\"analysis\":[],\"text\":\"www\"},{\"text\":\".\"},{\"analysis\":[{\"lex\":\"пример\"}],\"text\":\"пример\"},{\"text\":\".\"},{\"analysis\":[{\"lex\":\"рф\"}],\"text\":\"рф\"},{\"text\":\"/\"},{\"analysis\":[],\"text\":\"path\"},{\"text\":\"\\n\"}]", "пример.рф"),
                new CapturedEntity("https://bücher.de/path", "[{\"analysis\":[],\"text\":\"https\"},{\"text\":\"://\"},{\"analysis\":[],\"text\":\"bücher\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"de\"},{\"text\":\"/\"},{\"analysis\":[],\"text\":\"path\"},{\"text\":\"\\n\"}]", "bücher.de"),
                new CapturedEntity("'o'reilly@example.com'", "[{\"text\":\"'\"},{\"analysis\":[],\"text\":\"o\"},{\"text\":\"'\"},{\"analysis\":[],\"text\":\"reilly\"},{\"text\":\"@\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"'\\n\"}]", "example.com", "o'reilly@example.com"),
                new CapturedEntity("{a@example.com}", "[{\"text\":\"{\"},{\"analysis\":[],\"text\":\"a\"},{\"text\":\"@\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"}\\n\"}]", "example.com", "a@example.com"),
                new CapturedEntity("'{o'reilly@example.com}'", "[{\"text\":\"'{\"},{\"analysis\":[],\"text\":\"o\"},{\"text\":\"'\"},{\"analysis\":[],\"text\":\"reilly\"},{\"text\":\"@\"},{\"analysis\":[],\"text\":\"example\"},{\"text\":\".\"},{\"analysis\":[],\"text\":\"com\"},{\"text\":\"}'\\n\"}]", "example.com", "o'reilly@example.com"));
    }

    private record CapturedEntity(String source, String json, String domain, String entityText) {
        private CapturedEntity(String source, String json, String domain) {
            this(source, json, domain, source);
        }
    }
}
