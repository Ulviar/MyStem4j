package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Prepares Java strings for MyStem while retaining offset mappings for tokenizer use.
 */
public final class MystemTextPreprocessor {
    private MystemTextPreprocessor() {}

    public static MystemPreparedText prepare(String text) {
        return prepare(text, false);
    }

    /**
     * Prepares text for MyStem JSON-line protocol by replacing line separators with spaces.
     *
     * <p>Reusable and pooled MyStem clients use one stdout line as one response frame, so raw CR/LF characters cannot
     * be sent through that protocol. Offsets in the returned prepared text still map to the original string.
     *
     * @param text source text
     * @return prepared text with CR/LF replaced
     */
    public static MystemPreparedText prepareJsonLine(String text) {
        return prepare(text, true);
    }

    private static MystemPreparedText prepare(String text, boolean replaceLineSeparators) {
        Objects.requireNonNull(text, "text");
        StringBuilder prepared = new StringBuilder(text.length());
        ArrayList<MystemOffsetMapping> mappings = new ArrayList<>();
        ArrayList<MystemTextIssue> issues = new ArrayList<>();
        boolean lengthChanged = false;

        int index = 0;
        while (index < text.length()) {
            int preparedStart = prepared.length();
            char value = text.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1))) {
                    char lowSurrogate = text.charAt(index + 1);
                    if (isUnicodeNoncharacter(Character.toCodePoint(value, lowSurrogate))) {
                        prepared.append(' ');
                        issues.add(new MystemTextIssue(
                                MystemTextIssueType.NONCHARACTER, "Unicode noncharacter replaced with space", index, 2));
                        lengthChanged = true;
                    } else {
                        prepared.append(value).append(lowSurrogate);
                    }
                    appendMapping(mappings, preparedStart, prepared.length(), index, index + 2);
                    index += 2;
                } else {
                    prepared.append('\uFFFD');
                    issues.add(new MystemTextIssue(
                            MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired high surrogate", index, 1));
                    appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
                    index++;
                }
                continue;
            }
            if (Character.isLowSurrogate(value)) {
                prepared.append('\uFFFD');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired low surrogate", index, 1));
                appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            if (isUnicodeNoncharacter(value)) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.NONCHARACTER, "Unicode noncharacter replaced with space", index, 1));
                appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            if (replaceLineSeparators && (value == '\n' || value == '\r')) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.CONTROL_CHARACTER,
                        "Line separator replaced with space for JSON-line protocol",
                        index,
                        1));
                appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            if (isUnsafeControl(value)) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.CONTROL_CHARACTER, "Control character replaced with space", index, 1));
                appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            prepared.append(value);
            appendMapping(mappings, preparedStart, prepared.length(), index, index + 1);
            index++;
        }
        return new MystemPreparedText(text, prepared.toString(), lengthChanged ? mappings : List.of(), issues);
    }

    private static void appendMapping(
            ArrayList<MystemOffsetMapping> mappings,
            int preparedStart,
            int preparedEnd,
            int originalStart,
            int originalEnd) {
        if (!mappings.isEmpty()) {
            MystemOffsetMapping previous = mappings.getLast();
            if (canMerge(previous, preparedStart, preparedEnd, originalStart, originalEnd)) {
                mappings.set(
                        mappings.size() - 1,
                        new MystemOffsetMapping(
                                previous.preparedStart(), preparedEnd, previous.originalStart(), originalEnd));
                return;
            }
        }
        mappings.add(new MystemOffsetMapping(preparedStart, preparedEnd, originalStart, originalEnd));
    }

    private static boolean canMerge(
            MystemOffsetMapping previous,
            int preparedStart,
            int preparedEnd,
            int originalStart,
            int originalEnd) {
        return previous.preparedEnd() == preparedStart
                && previous.originalEnd() == originalStart
                && previous.originalStart() - previous.preparedStart() == originalStart - preparedStart
                && previous.preparedEnd() - previous.preparedStart()
                        == previous.originalEnd() - previous.originalStart()
                && preparedEnd - preparedStart == originalEnd - originalStart;
    }

    private static boolean isUnsafeControl(char value) {
        return Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t';
    }

    private static boolean isUnicodeNoncharacter(int codePoint) {
        return (codePoint >= 0xFDD0 && codePoint <= 0xFDEF) || (codePoint & 0xFFFE) == 0xFFFE;
    }
}
