package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Replaces characters unsafe for MyStem and retains a mapping to the original Java string.
 *
 * <p>Use {@link #prepare(String)} for ordinary input and {@link #prepareJsonLine(String)} when
 * a reusable MyStem process expects one request per line. Send {@link MystemPreparedText#text()}
 * to MyStem, then pass the same {@link MystemPreparedText} to
 * {@link MystemJsonParser#parse(MystemPreparedText, String)}. Parsing only the prepared string would
 * lose original-text offsets when a replacement changes the UTF-16 length.
 *
 * <p>Preparation does not normalize case, combine Unicode sequences, remove soft hyphens, trim
 * whitespace, or collapse adjacent spaces. Each replacement is recorded in the returned issues;
 * the original string is retained unchanged. Static methods are safe to call concurrently.
 */
public final class MystemTextPreprocessor {
    private MystemTextPreprocessor() {}

    /**
     * Replaces unsafe input characters while preserving a mapping to the original text.
     *
     * <table>
     * <caption>Character replacements</caption>
     * <thead><tr><th scope="col">Input</th><th scope="col">Replacement</th><th scope="col">Issue</th></tr></thead>
     * <tbody>
     * <tr><td>Unpaired high or low surrogate</td><td>{@code U+FFFD}</td>
     *     <td>{@link MystemTextIssueType#UNPAIRED_SURROGATE}</td></tr>
     * <tr><td>ISO control character, including NUL, other than CR, LF, or tab</td><td>One space</td>
     *     <td>{@link MystemTextIssueType#CONTROL_CHARACTER}</td></tr>
     * <tr><td>Unicode noncharacter: {@code U+FDD0..U+FDEF} or a code point ending in
     *     {@code FFFE} or {@code FFFF}</td><td>One space</td>
     *     <td>{@link MystemTextIssueType#NONCHARACTER}</td></tr>
     * </tbody>
     * </table>
     *
     * <p>Each replacement creates one issue with its range in the original text. A supplementary
     * noncharacter occupies two UTF-16 code units but becomes one space; use the returned mapping
     * rather than assuming that offsets are unchanged. All other characters, including valid
     * supplementary characters, soft hyphens, combining marks, CR, LF, and tab, are preserved.
     *
     * @param text caller's original Java string
     * @return immutable prepared text, original-text offset mapping, and replacement issues
     * @throws NullPointerException if text is {@code null}
     */
    public static MystemPreparedText prepare(String text) {
        return prepare(text, false);
    }

    /**
     * Applies {@link #prepare(String)} replacements and also replaces each CR and LF with a space.
     *
     * <p>Reusable and pooled MyStem clients use one stdout line as one response frame, so raw CR/LF
     * characters cannot be sent through that protocol. A CRLF pair becomes two spaces and produces
     * two {@link MystemTextIssueType#CONTROL_CHARACTER} issues. Tab is preserved. This method does
     * not append a request terminator; the client writing to MyStem owns framing.
     *
     * @param text caller's original Java string, possibly containing multiple lines
     * @return immutable prepared text, original-text offset mapping, and all replacement issues
     * @throws NullPointerException if text is {@code null}
     */
    public static MystemPreparedText prepareJsonLine(String text) {
        return prepare(text, true);
    }

    private static MystemPreparedText prepare(String text, boolean replaceLineSeparators) {
        Objects.requireNonNull(text, "text");
        StringBuilder prepared = new StringBuilder(text.length());
        ArrayList<MystemOffsetMapping> mappings = new ArrayList<>();
        ArrayList<MystemTextIssue> issues = new ArrayList<>();
        boolean mappingRequired = false;

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
                    } else {
                        prepared.append(value).append(lowSurrogate);
                    }
                    mappingRequired =
                            appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 2);
                    index += 2;
                } else {
                    prepared.append('\uFFFD');
                    issues.add(new MystemTextIssue(
                            MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired high surrogate", index, 1));
                    mappingRequired =
                            appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
                    index++;
                }
                continue;
            }
            if (Character.isLowSurrogate(value)) {
                prepared.append('\uFFFD');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired low surrogate", index, 1));
                mappingRequired =
                        appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            if (isUnicodeNoncharacter(value)) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.NONCHARACTER, "Unicode noncharacter replaced with space", index, 1));
                mappingRequired =
                        appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
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
                mappingRequired =
                        appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            if (isUnsafeControl(value)) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.CONTROL_CHARACTER, "Control character replaced with space", index, 1));
                mappingRequired =
                        appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
                index++;
                continue;
            }
            prepared.append(value);
            mappingRequired =
                    appendMappingIfNeeded(mappings, mappingRequired, preparedStart, prepared.length(), index, index + 1);
            index++;
        }
        return new MystemPreparedText(text, prepared.toString(), mappingRequired ? mappings : List.of(), issues);
    }

    private static boolean appendMappingIfNeeded(
            ArrayList<MystemOffsetMapping> mappings,
            boolean mappingRequired,
            int preparedStart,
            int preparedEnd,
            int originalStart,
            int originalEnd) {
        boolean lengthChanged = preparedEnd - preparedStart != originalEnd - originalStart;
        if (!mappingRequired && !lengthChanged) {
            return false;
        }
        if (!mappingRequired) {
            if (preparedStart > 0 || originalStart > 0) {
                appendMapping(mappings, 0, preparedStart, 0, originalStart);
            }
            mappingRequired = true;
        }
        appendMapping(mappings, preparedStart, preparedEnd, originalStart, originalEnd);
        return mappingRequired;
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
