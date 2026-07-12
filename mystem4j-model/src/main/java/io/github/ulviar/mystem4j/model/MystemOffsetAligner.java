package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

final class MystemOffsetAligner {
    private static final int SOFT_HYPHEN = 0x00AD;

    private final String alignmentText;
    private final IntUnaryOperator originalOffsetFor;
    private final ArrayList<MystemTextIssue> issues = new ArrayList<>();
    private int cursor;

    MystemOffsetAligner(String originalText) {
        this(originalText, IntUnaryOperator.identity());
    }

    MystemOffsetAligner(String alignmentText, IntUnaryOperator originalOffsetFor) {
        this.alignmentText = Objects.requireNonNull(alignmentText, "alignmentText");
        this.originalOffsetFor = Objects.requireNonNull(originalOffsetFor, "originalOffsetFor");
    }

    MystemTextRange align(String tokenText) {
        if (tokenText.isEmpty()) {
            int originalCursor = originalOffsetFor.applyAsInt(cursor);
            return new MystemTextRange(originalCursor, originalCursor);
        }
        int index = alignmentText.indexOf(tokenText, cursor);
        FuzzyMatch fuzzyMatch = findFuzzyMatchBefore(tokenText, index);
        if (fuzzyMatch != null) {
            cursor = fuzzyMatch.endOffset();
            return mappedRange(fuzzyMatch.startOffset(), fuzzyMatch.endOffset());
        }
        if (index < 0) {
            issues.add(new MystemTextIssue(
                    MystemTextIssueType.UNMATCHED_TOKEN,
                    "Could not align MyStem token to original text: " + tokenText,
                    originalOffsetFor.applyAsInt(cursor),
                    tokenText.length()));
            return MystemTextRange.unknown();
        }
        cursor = index + tokenText.length();
        return mappedRange(index, cursor);
    }

    private FuzzyMatch findFuzzyMatchBefore(String tokenText, int exactIndex) {
        // A later exact occurrence must not win over the current occurrence when MyStem
        // dropped a character such as soft hyphen from the current token surface.
        for (int start = cursor;
                start < alignmentText.length() && (exactIndex < 0 || start < exactIndex);
                start += Character.charCount(alignmentText.codePointAt(start))) {
            int end = matchIgnoringMyStemDroppedCharacters(tokenText, start);
            if (end >= 0) {
                return new FuzzyMatch(start, end);
            }
        }
        return null;
    }

    private int matchIgnoringMyStemDroppedCharacters(String tokenText, int start) {
        int textIndex = start;
        int tokenIndex = 0;
        while (textIndex < alignmentText.length() && tokenIndex < tokenText.length()) {
            int textCodePoint = alignmentText.codePointAt(textIndex);
            if (isMyStemDroppedCharacter(textCodePoint)) {
                textIndex += Character.charCount(textCodePoint);
                continue;
            }
            int tokenCodePoint = tokenText.codePointAt(tokenIndex);
            if (textCodePoint != tokenCodePoint) {
                return -1;
            }
            textIndex += Character.charCount(textCodePoint);
            tokenIndex += Character.charCount(tokenCodePoint);
        }
        return tokenIndex == tokenText.length() ? textIndex : -1;
    }

    private static boolean isMyStemDroppedCharacter(int codePoint) {
        return codePoint == SOFT_HYPHEN;
    }

    private MystemTextRange mappedRange(int startOffset, int endOffset) {
        return new MystemTextRange(
                originalOffsetFor.applyAsInt(startOffset), originalOffsetFor.applyAsInt(endOffset));
    }

    List<MystemTextIssue> issues() {
        return List.copyOf(issues);
    }

    private record FuzzyMatch(int startOffset, int endOffset) {}
}
