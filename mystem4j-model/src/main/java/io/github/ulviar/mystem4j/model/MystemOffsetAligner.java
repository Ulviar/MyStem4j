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
    private int nextSoftHyphen;

    MystemOffsetAligner(String originalText) {
        this(originalText, IntUnaryOperator.identity());
    }

    MystemOffsetAligner(String alignmentText, IntUnaryOperator originalOffsetFor) {
        this.alignmentText = Objects.requireNonNull(alignmentText, "alignmentText");
        this.originalOffsetFor = Objects.requireNonNull(originalOffsetFor, "originalOffsetFor");
        this.nextSoftHyphen = alignmentText.indexOf(SOFT_HYPHEN);
    }

    MystemTextRange align(String tokenText) {
        if (tokenText.isEmpty()) {
            int originalCursor = originalOffsetFor.applyAsInt(cursor);
            return new MystemTextRange(originalCursor, originalCursor);
        }
        Match match = findMatch(tokenText);
        if (match == null) {
            issues.add(new MystemTextIssue(
                    MystemTextIssueType.UNMATCHED_TOKEN,
                    "Could not align MyStem token to original text: " + tokenText,
                    originalOffsetFor.applyAsInt(cursor),
                    tokenText.length()));
            return MystemTextRange.unknown();
        }
        cursor = match.endOffset();
        return mappedRange(match.startOffset(), cursor);
    }

    private Match findMatch(String tokenText) {
        if (alignmentText.startsWith(tokenText, cursor)) {
            return new Match(cursor, cursor + tokenText.length());
        }
        if (tokenText.indexOf(SOFT_HYPHEN) >= 0 || !hasSoftHyphenFromCursor()) {
            int exactIndex = findExactMatch(tokenText);
            return exactIndex < 0 ? null : new Match(exactIndex, exactIndex + tokenText.length());
        }
        return findExactOrFuzzyMatch(tokenText);
    }

    private int findExactMatch(String tokenText) {
        // Keep exact matching in UTF-16 units, including literal isolated surrogate surfaces.
        int[] prefixes = prefixLengths(tokenText.length(), tokenText::charAt);
        int matched = 0;
        for (int index = cursor; index < alignmentText.length(); index++) {
            char value = alignmentText.charAt(index);
            while (matched > 0 && value != tokenText.charAt(matched)) {
                matched = prefixes[matched - 1];
            }
            if (value == tokenText.charAt(matched)) {
                matched++;
                if (matched == tokenText.length()) {
                    return index + 1 - matched;
                }
            }
        }
        return -1;
    }

    private Match findExactOrFuzzyMatch(String tokenText) {
        // Advance exact UTF-16 and fuzzy code-point KMP searches together. Searching the whole
        // suffix for an exact match first would rescan it for every successful fuzzy token.
        int[] exactPrefixes = prefixLengths(tokenText.length(), tokenText::charAt);
        int exactMatched = 0;
        int exactIndex = -1;
        int[] pattern = tokenText.codePoints().toArray();
        int[] prefixes = prefixLengths(pattern.length, index -> pattern[index]);
        int[] starts = new int[pattern.length];
        int nextStart = 0;
        int previousEnd = cursor;
        int matched = 0;
        for (int textIndex = cursor; textIndex < alignmentText.length(); ) {
            int codePointStart = textIndex;
            int value = alignmentText.codePointAt(textIndex);
            textIndex += Character.charCount(value);
            // Read original code points before dropping SHY: separated surrogate halves must
            // not become a pair. Exact matches can still start/end inside an original pair.
            for (int index = codePointStart; index < textIndex && exactIndex < 0; index++) {
                char unit = alignmentText.charAt(index);
                while (exactMatched > 0 && unit != tokenText.charAt(exactMatched)) {
                    exactMatched = exactPrefixes[exactMatched - 1];
                }
                if (unit == tokenText.charAt(exactMatched)) {
                    exactMatched++;
                    if (exactMatched == tokenText.length()) {
                        exactIndex = index + 1 - exactMatched;
                    }
                }
            }
            if (value == SOFT_HYPHEN) {
                continue;
            }
            // The earliest start includes all SHY between the preceding code point and this one.
            // Retain only the last pattern.length starts, rather than a map for the whole source.
            starts[nextStart] = previousEnd;
            nextStart = (nextStart + 1) % starts.length;
            previousEnd = textIndex;
            while (matched > 0 && value != pattern[matched]) {
                matched = prefixes[matched - 1];
            }
            if (value == pattern[matched]) {
                matched++;
                if (matched == pattern.length) {
                    int start = starts[nextStart];
                    // Every exact match starting before this fuzzy span must already have ended.
                    return exactIndex < 0 || start < exactIndex
                            ? new Match(start, textIndex)
                            : new Match(exactIndex, exactIndex + tokenText.length());
                }
            }
            if (exactIndex >= 0) {
                int candidateIndex = nextStart - matched;
                if (candidateIndex < 0) {
                    candidateIndex += starts.length;
                }
                int earliestStart = matched == 0 ? previousEnd : starts[candidateIndex];
                if (earliestStart >= exactIndex) {
                    return new Match(exactIndex, exactIndex + tokenText.length());
                }
            }
        }
        return exactIndex < 0 ? null : new Match(exactIndex, exactIndex + tokenText.length());
    }

    private boolean hasSoftHyphenFromCursor() {
        // The cursor only advances. Scan each suffix once, rather than once per sparse token.
        if (nextSoftHyphen >= 0 && nextSoftHyphen < cursor) {
            nextSoftHyphen = alignmentText.indexOf(SOFT_HYPHEN, cursor);
        }
        return nextSoftHyphen >= 0;
    }

    private static int[] prefixLengths(int length, IntUnaryOperator valueAt) {
        int[] prefixes = new int[length];
        int matched = 0;
        for (int index = 1; index < length; index++) {
            int value = valueAt.applyAsInt(index);
            while (matched > 0 && value != valueAt.applyAsInt(matched)) {
                matched = prefixes[matched - 1];
            }
            if (value == valueAt.applyAsInt(matched)) {
                matched++;
            }
            prefixes[index] = matched;
        }
        return prefixes;
    }

    private MystemTextRange mappedRange(int startOffset, int endOffset) {
        return new MystemTextRange(
                originalOffsetFor.applyAsInt(startOffset), originalOffsetFor.applyAsInt(endOffset));
    }

    List<MystemTextIssue> issues() {
        return List.copyOf(issues);
    }

    private record Match(int startOffset, int endOffset) {}
}
