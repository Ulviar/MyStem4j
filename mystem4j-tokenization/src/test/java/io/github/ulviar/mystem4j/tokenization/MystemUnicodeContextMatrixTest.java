package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ulviar.mystem4j.model.MystemDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class MystemUnicodeContextMatrixTest {
    private static final int[] REPRESENTATIVE_CODE_POINTS = {
        0x0000,
        0x0009,
        0x000A,
        0x000D,
        0x0023,
        0x002B,
        0x007F,
        0x00A0,
        0x00AD,
        0x0301,
        0x0341,
        0x034F,
        0x055E,
        0x058E,
        0x06DE,
        0x0903,
        0x1801,
        0x200B,
        0x200D,
        0x2028,
        0x2029,
        0x2060,
        0x2160,
        0x2E2D,
        0x3000,
        0xE000,
        0xFDD0,
        0xFE0F,
        0x1F600,
        0xE0100,
        0x10FFFF
    };
    private static final String[] BASES = {"Двух", "Вуха", "Four", "1122"};
    private static final MystemSearchTokenizer TOKENIZER =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void representativeUnicodeCategoriesPreserveACompleteTokenPartitionInEveryContext() {
        for (int codePoint : REPRESENTATIVE_CODE_POINTS) {
            for (String base : BASES) {
                for (Context context : Context.values()) {
                    assertTokenizationInvariants(context.text(base, codePoint), codePoint, base, context);
                }
            }
        }
    }

    @Test
    void knownLegacyCharacterGroupsKeepTheirClassificationBoundaries() {
        assertSingleType("1\u037E2", MystemSearchTokenType.NUMBER);
        assertSingleType("a\u055Eb", MystemSearchTokenType.WORD);
        assertSingleType("a\u06DEb", MystemSearchTokenType.WORD);
        assertSingleType("a\u0301b", MystemSearchTokenType.WORD);
        assertMultipleTokens("a\u0898b");
        assertMultipleTokens("a\u17B4b");
        assertMultipleTokens("1\u2E2D2");
    }

    @Test
    void allUnicodeScalarValuesPreserveTokenizationInvariantsAcrossRotatingContexts() {
        assumeTrue(Boolean.getBoolean("mystem4j.unicodeContextStress"),
                "Run the unicodeContextStressTest task to enable the exhaustive context matrix.");
        int scalarValuesChecked = 0;
        Context[] contexts = Context.values();
        for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                continue;
            }
            String base = BASES[scalarValuesChecked % BASES.length];
            Context context = contexts[(scalarValuesChecked / BASES.length) % contexts.length];
            assertTokenizationInvariants(context.text(base, codePoint), codePoint, base, context);
            scalarValuesChecked++;
        }
        assertEquals(1_112_064, scalarValuesChecked);

        int fullMatrixCodePointsChecked = 0;
        for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            if (!isContextSensitiveDefinedCodePoint(codePoint)) {
                continue;
            }
            for (String base : BASES) {
                for (Context context : contexts) {
                    assertTokenizationInvariants(context.text(base, codePoint), codePoint, base, context);
                }
            }
            fullMatrixCodePointsChecked++;
        }
        assertTrue(fullMatrixCodePointsChecked > 0);
    }

    private static boolean isContextSensitiveDefinedCodePoint(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)
                || !Character.isDefined(codePoint)
                || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type != Character.LOWERCASE_LETTER
                && type != Character.UPPERCASE_LETTER
                && type != Character.OTHER_LETTER
                && type != Character.DECIMAL_DIGIT_NUMBER;
    }

    private static void assertSingleType(String text, MystemSearchTokenType expectedType) {
        List<MystemSearchToken> tokens = tokenize(text);
        assertEquals(1, tokens.size(), text + ": " + tokens);
        assertEquals(expectedType, tokens.getFirst().type(), text);
        assertPartition(text, tokens, "classification boundary");
    }

    private static void assertMultipleTokens(String text) {
        List<MystemSearchToken> tokens = tokenize(text);
        assertTrue(tokens.size() > 1, text + ": " + tokens);
        assertPartition(text, tokens, "classification boundary");
    }

    private static void assertTokenizationInvariants(String text, int codePoint, String base, Context context) {
        List<MystemSearchToken> first = tokenize(text);
        List<MystemSearchToken> second = tokenize(text);
        String label = "U+" + Integer.toHexString(codePoint).toUpperCase()
                + ", base=" + base
                + ", context=" + context;

        assertEquals(first, second, () -> "Non-deterministic tokenization for " + label);
        assertPartition(text, first, label);
    }

    private static List<MystemSearchToken> tokenize(String text) {
        return TOKENIZER.tokenize(new MystemDocument(text, List.of(), List.of()));
    }

    private static void assertPartition(String text, List<MystemSearchToken> tokens, String label) {
        int cursor = 0;
        StringBuilder reconstructed = new StringBuilder(text.length());
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset(), () -> "Gap or overlap for " + label + ": " + tokens);
            assertTrue(token.endOffset() > token.startOffset(), () -> "Empty token for " + label + ": " + token);
            assertTrue(token.endOffset() <= text.length(), () -> "Offset outside input for " + label + ": " + token);
            assertEquals(text.substring(token.startOffset(), token.endOffset()), token.text(), label);
            assertFalse(token.forms().isEmpty(), () -> "Missing forms for " + label + ": " + token);
            assertFalse(
                    token.forms().stream().anyMatch(form -> form.text().isEmpty()),
                    () -> "Empty form for " + label + ": " + token);
            reconstructed.append(token.text());
            cursor = token.endOffset();
        }
        assertEquals(text.length(), cursor, () -> "Uncovered suffix for " + label + ": " + tokens);
        assertEquals(text, reconstructed.toString(), label);
    }

    private enum Context {
        BEFORE {
            @Override
            String text(String base, String character) {
                return character + base;
            }
        },
        AFTER {
            @Override
            String text(String base, String character) {
                return base + character;
            }
        },
        AROUND {
            @Override
            String text(String base, String character) {
                return character + base + character;
            }
        },
        BEFORE_PUNCTUATION {
            @Override
            String text(String base, String character) {
                return base + character + ",";
            }
        },
        INSIDE {
            @Override
            String text(String base, String character) {
                int middle = base.offsetByCodePoints(0, base.codePointCount(0, base.length()) / 2);
                return base.substring(0, middle) + character + base.substring(middle);
            }
        },
        BETWEEN_DUPLICATES {
            @Override
            String text(String base, String character) {
                return base + character + base;
            }
        };

        final String text(String base, int codePoint) {
            return text(base, Character.toString(codePoint));
        }

        abstract String text(String base, String character);
    }
}
