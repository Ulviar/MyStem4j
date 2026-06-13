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
        if (index < 0) {
            MystemTextRange fuzzyRange = fuzzyAlign(tokenText);
            if (fuzzyRange.startOffset() >= 0) {
                return fuzzyRange;
            }
            issues.add(new MystemTextIssue(
                    MystemTextIssueType.UNMATCHED_TOKEN,
                    "Could not align MyStem token to original text: " + tokenText,
                    originalOffsetFor.applyAsInt(cursor),
                    tokenText.length()));
            return MystemTextRange.unknown();
        }
        cursor = index + tokenText.length();
        return new MystemTextRange(originalOffsetFor.applyAsInt(index), originalOffsetFor.applyAsInt(cursor));
    }

    private MystemTextRange fuzzyAlign(String tokenText) {
        // Fallback for trusted MyStem JSON only: MyStem may drop selected code points
        // such as soft hyphen from token text while offsets still need to point to the
        // original input. Normal alignment uses String.indexOf from the current cursor.
        for (int start = cursor; start < alignmentText.length(); start += Character.charCount(alignmentText.codePointAt(start))) {
            int end = matchIgnoringMyStemDroppedCharacters(tokenText, start);
            if (end >= 0) {
                cursor = end;
                return new MystemTextRange(originalOffsetFor.applyAsInt(start), originalOffsetFor.applyAsInt(end));
            }
        }
        return MystemTextRange.unknown();
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

    List<MystemTextIssue> issues() {
        return List.copyOf(issues);
    }
}
