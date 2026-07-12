package io.github.ulviar.mystem4j.tokenization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.MystemRawResult;
import io.github.ulviar.mystem4j.model.MystemDocument;
import io.github.ulviar.mystem4j.model.MystemJsonParser;
import io.github.ulviar.mystem4j.model.MystemPreparedText;
import io.github.ulviar.mystem4j.model.MystemTextIssueType;
import io.github.ulviar.mystem4j.model.MystemTextPreprocessor;
import io.github.ulviar.mystem4j.model.MystemToken;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemUnicodeContextIntegrationTest {
    private static final int[] REPRESENTATIVE_CODE_POINTS = {
        0x0000,
        0x000A,
        0x0023,
        0x002B,
        0x00A0,
        0x00AD,
        0x0301,
        0x034F,
        0x055E,
        0x06DE,
        0x0903,
        0x1801,
        0x200B,
        0x200D,
        0x2028,
        0x2060,
        0x2160,
        0x3000,
        0xE000,
        0xFDD0,
        0xFE0F,
        0x1F600,
        0xE0100
    };
    private static final String[] BASES = {"Двух", "Вуха", "Four", "1122"};

    private final MystemJsonParser parser = new MystemJsonParser();
    private final MystemSearchTokenizer tokenizer =
            new MystemSearchTokenizer(MystemSearchTokenizerOptions.entityAware());

    @Test
    void preservesOffsetsAcrossUnicodeCategoriesWordKindsAndInsertionContexts() {
        try (MystemClient client = newClient()) {
            for (int codePoint : REPRESENTATIVE_CODE_POINTS) {
                for (String base : BASES) {
                    for (Context context : Context.values()) {
                        assertContext(client, codePoint, base, context);
                    }
                }
            }
        }
    }

    @Test
    void keepsRepeatedSoftHyphenAndExactWordsInSourceOrder() {
        try (MystemClient client = newClient()) {
            for (String text : List.of(
                    "О\u00ADдин Один",
                    "Один О\u00ADдин",
                    "О\u00ADдин О\u00ADдин",
                    "О\u00ADд\u00ADи\u00ADн Один О\u00ADдин")) {
                MystemDocument document = analyze(client, text);

                assertModelOffsets(text, document, "repeated soft-hyphen input", "<captured separately>");
                assertSearchPartition(text, tokenizer.tokenize(document), "repeated soft-hyphen input");
            }
        }
    }

    private void assertContext(MystemClient client, int codePoint, String base, Context context) {
        String text = context.text(base, codePoint);
        MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine(text);
        MystemRawResult raw = client.analyze(prepared.text());
        MystemDocument document = parser.parse(prepared, raw.output());
        String label = "U+" + Integer.toHexString(codePoint).toUpperCase()
                + ", category=" + Character.getType(codePoint)
                + ", base=" + base
                + ", context=" + context;

        assertModelOffsets(text, document, label, raw.output());
        assertSearchPartition(text, tokenizer.tokenize(document), label);
    }

    private MystemDocument analyze(MystemClient client, String text) {
        MystemPreparedText prepared = MystemTextPreprocessor.prepareJsonLine(text);
        MystemRawResult raw = client.analyze(prepared.text());
        return parser.parse(prepared, raw.output());
    }

    private static void assertModelOffsets(
            String text, MystemDocument document, String label, String rawOutput) {
        assertTrue(
                document.issues().stream().noneMatch(issue -> issue.type() == MystemTextIssueType.UNMATCHED_TOKEN),
                () -> label + " has unmatched model tokens: input=" + printable(text)
                        + ", issues=" + document.issues()
                        + ", output=" + rawOutput);
        int cursor = 0;
        for (MystemToken token : document.tokens()) {
            assertTrue(token.hasKnownOffsets(), () -> label + " has unknown offsets: " + token);
            assertTrue(token.startOffset() >= cursor, () -> label + " has overlapping offsets: " + document.tokens());
            assertTrue(token.endOffset() >= token.startOffset(), () -> label + " has reversed offsets: " + token);
            assertTrue(token.endOffset() <= text.length(), () -> label + " has offsets outside input: " + token);
            cursor = token.endOffset();
        }
    }

    private static void assertSearchPartition(String text, List<MystemSearchToken> tokens, String label) {
        int cursor = 0;
        StringBuilder reconstructed = new StringBuilder(text.length());
        for (MystemSearchToken token : tokens) {
            assertEquals(cursor, token.startOffset(), () -> label + " has a gap or overlap: " + tokens);
            assertEquals(
                    text.substring(token.startOffset(), token.endOffset()),
                    token.text(),
                    () -> label + " has an invalid source slice: " + token);
            assertTrue(token.endOffset() > token.startOffset(), () -> label + " has an empty token: " + token);
            assertTrue(!token.forms().isEmpty(), () -> label + " has no search forms: " + token);
            reconstructed.append(token.text());
            cursor = token.endOffset();
        }
        assertEquals(text.length(), cursor, () -> label + " has an uncovered suffix: " + tokens);
        assertEquals(text, reconstructed.toString(), label);
    }

    private static MystemClient newClient() {
        return Mystem.builder()
                .executable(Path.of(System.getProperty("mystem4j.executable")))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .build())
                .requestTimeout(Duration.ofSeconds(30))
                .session()
                .build();
    }

    private static String printable(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
                result.append("<U+").append(Integer.toHexString(codePoint).toUpperCase()).append('>');
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
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
