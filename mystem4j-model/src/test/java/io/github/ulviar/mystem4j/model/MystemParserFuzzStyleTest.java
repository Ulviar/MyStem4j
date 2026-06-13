package io.github.ulviar.mystem4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MystemParserFuzzStyleTest {
    private static final int GENERATED_DOCUMENT_CASES = 500;
    private static final int MALFORMED_JSON_CASES = 2_000;
    private static final int GRAMMAR_CASES = 10_000;
    private static final long SEED = 0x4D_59_53_54_45_4D_34_4AL;

    private final MystemJsonParser parser = new MystemJsonParser();

    @Test
    void parsesGeneratedUnicodeJsonWithStableOffsets() {
        Random random = new Random(SEED);
        for (int attempt = 0; attempt < GENERATED_DOCUMENT_CASES; attempt++) {
            GeneratedDocument generated = generateDocument(random);

            MystemDocument document = parser.parse(generated.originalText(), generated.json());

            assertEquals(generated.originalText(), document.originalText(), "attempt " + attempt);
            assertEquals(generated.tokens().size(), document.tokens().size(), "attempt " + attempt);
            assertEquals(List.of(), document.issues(), "attempt " + attempt);
            for (int index = 0; index < generated.tokens().size(); index++) {
                GeneratedToken expected = generated.tokens().get(index);
                MystemToken actual = document.tokens().get(index);
                assertEquals(expected.text(), actual.text(), "attempt " + attempt + ", token " + index);
                assertEquals(expected.startOffset(), actual.startOffset(), "attempt " + attempt + ", token " + index);
                assertEquals(expected.endOffset(), actual.endOffset(), "attempt " + attempt + ", token " + index);
                assertEquals(expected.lemma(), actual.analyses().get(0).lemma(), "attempt " + attempt + ", token " + index);
                assertEquals(expected.grammar(), actual.analyses().get(0).grammar().raw(), "attempt " + attempt + ", token " + index);
                assertEquals(
                        expected.weight(),
                        actual.analyses().get(0).weight().orElseThrow(),
                        0.000_000_1,
                        "attempt " + attempt + ", token " + index);
            }
        }
    }

    @Test
    void randomJsonLikeInputEitherParsesOrFailsWithParseException() {
        Random random = new Random(SEED ^ 0x4A_53_4F_4EL);
        for (int attempt = 0; attempt < MALFORMED_JSON_CASES; attempt++) {
            String candidate = randomJsonLikeString(random);
            try {
                parser.parse("мама папа", candidate);
            } catch (MystemJsonParseException expected) {
                assertFalse(expected.getMessage().isBlank(), "attempt " + attempt);
            } catch (RuntimeException unexpected) {
                fail("Unexpected exception for attempt " + attempt + " and input: " + candidate, unexpected);
            }
        }
    }

    @Test
    void randomGrammarStringsDoNotViolateModelInvariants() {
        Random random = new Random(SEED ^ 0x47_52_41_4DL);
        for (int attempt = 0; attempt < GRAMMAR_CASES; attempt++) {
            int attemptIndex = attempt;
            String raw = randomGrammarString(random);

            MystemGrammar grammar = MystemGrammarParser.parse(raw);

            assertEquals(raw, grammar.raw(), "attempt " + attemptIndex);
            grammar.partOfSpeech().ifPresent(partOfSpeech ->
                    assertFalse(partOfSpeech.isBlank(), "attempt " + attemptIndex));
            grammar.commonGrammemes().forEach(grammeme ->
                    assertFalse(grammeme.isBlank(), "attempt " + attemptIndex));
            assertFalse(grammar.variants().isEmpty(), "attempt " + attemptIndex);
            grammar.variants().forEach(variant -> variant.grammemes().forEach(grammeme ->
                    assertFalse(grammeme.isBlank(), "attempt " + attemptIndex)));
            grammar.allGrammemes().forEach(grammeme ->
                    assertFalse(grammeme.isBlank(), "attempt " + attemptIndex));
        }
    }

    private static GeneratedDocument generateDocument(Random random) {
        StringBuilder text = new StringBuilder();
        StringBuilder json = new StringBuilder("[");
        ArrayList<GeneratedToken> tokens = new ArrayList<>();
        int tokenCount = 1 + random.nextInt(10);
        for (int index = 0; index < tokenCount; index++) {
            if (index > 0) {
                text.append(randomSeparator(random));
                json.append(',');
            }
            String token = randomTokenSurface(random, 1 + random.nextInt(5));
            String lemma = randomTokenSurface(random, 1 + random.nextInt(5)).toLowerCase(java.util.Locale.ROOT);
            String grammar = randomGrammarString(random);
            double weight = random.nextDouble();
            int start = text.length();
            text.append(token);
            int end = text.length();
            tokens.add(new GeneratedToken(token, start, end, lemma, grammar, weight));
            json.append("{\"analysis\":[{\"lex\":")
                    .append(jsonString(lemma))
                    .append(",\"gr\":")
                    .append(jsonString(grammar))
                    .append(",\"wt\":")
                    .append(Double.toString(weight))
                    .append("}],\"text\":")
                    .append(jsonString(token))
                    .append('}');
        }
        json.append(']');
        return new GeneratedDocument(text.toString(), json.toString(), tokens);
    }

    private static String randomJsonLikeString(Random random) {
        int length = random.nextInt(256);
        String alphabet = "[]{}:,\"\\abcdefghijklmnopqrstuvwxyz0123456789 \n\r\t\u0000\uD83D\uDE00";
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }

    private static String randomGrammarString(Random random) {
        int parts = random.nextInt(8);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < parts; index++) {
            if (index > 0) {
                result.append(randomDelimiter(random));
            }
            result.append(randomGrammarAtom(random));
        }
        return result.toString();
    }

    private static char randomDelimiter(Random random) {
        return switch (random.nextInt(5)) {
            case 0 -> ',';
            case 1 -> '=';
            case 2 -> '|';
            case 3 -> '(';
            default -> ')';
        };
    }

    private static String randomGrammarAtom(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> "S";
            case 1 -> "A";
            case 2 -> "V";
            case 3 -> "им";
            case 4 -> "род";
            case 5 -> "жен";
            case 6 -> "ед";
            case 7 -> "мн";
            case 8 -> "полн";
            case 9 -> " ";
            case 10 -> randomSurface(random, 1 + random.nextInt(3));
            default -> "";
        };
    }

    private static String randomSurface(Random random, int codePoints) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < codePoints; index++) {
            result.appendCodePoint(randomSurfaceCodePoint(random));
        }
        return result.toString();
    }

    private static String randomTokenSurface(Random random, int codePoints) {
        StringBuilder result = new StringBuilder();
        result.appendCodePoint(randomWordCodePoint(random));
        for (int index = 1; index < codePoints; index++) {
            result.appendCodePoint(random.nextInt(4) == 0 ? randomSurfaceCodePoint(random) : randomWordCodePoint(random));
        }
        return result.toString();
    }

    private static int randomWordCodePoint(Random random) {
        return switch (random.nextInt(5)) {
            case 0 -> 'а' + random.nextInt(32);
            case 1 -> 'А' + random.nextInt(32);
            case 2 -> 'a' + random.nextInt(26);
            case 3 -> 'A' + random.nextInt(26);
            default -> '0' + random.nextInt(10);
        };
    }

    private static int randomSurfaceCodePoint(Random random) {
        return switch (random.nextInt(14)) {
            case 0 -> 'а' + random.nextInt(32);
            case 1 -> 'A' + random.nextInt(26);
            case 2 -> '0' + random.nextInt(10);
            case 3 -> '-';
            case 4 -> '_';
            case 5 -> '+';
            case 6 -> '.';
            case 7 -> 0x00AD;
            case 8 -> 0x0301;
            case 9 -> 0x200D;
            case 10 -> 0x1F600 + random.nextInt(80);
            case 11 -> '"';
            case 12 -> '\\';
            default -> 'я';
        };
    }

    private static String randomSeparator(Random random) {
        return switch (random.nextInt(5)) {
            case 0 -> " ";
            case 1 -> ", ";
            case 2 -> "\n";
            case 3 -> "\t";
            default -> ".";
        };
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            switch (codePoint) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (codePoint < 0x20) {
                        result.append(String.format("\\u%04x", codePoint));
                    } else {
                        result.appendCodePoint(codePoint);
                    }
                }
            }
        }
        result.append('"');
        return result.toString();
    }

    private record GeneratedDocument(String originalText, String json, List<GeneratedToken> tokens) {}

    private record GeneratedToken(
            String text,
            int startOffset,
            int endOffset,
            String lemma,
            String grammar,
            double weight) {}
}
