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
        Objects.requireNonNull(text, "text");
        StringBuilder prepared = new StringBuilder(text.length());
        ArrayList<MystemOffsetMapping> mappings = new ArrayList<>();
        ArrayList<MystemTextIssue> issues = new ArrayList<>();

        int index = 0;
        while (index < text.length()) {
            int preparedStart = prepared.length();
            char value = text.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1))) {
                    prepared.append(value).append(text.charAt(index + 1));
                    mappings.add(new MystemOffsetMapping(preparedStart, prepared.length(), index, index + 2));
                    index += 2;
                } else {
                    prepared.append('\uFFFD');
                    issues.add(new MystemTextIssue(
                            MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired high surrogate", index, 1));
                    mappings.add(new MystemOffsetMapping(preparedStart, prepared.length(), index, index + 1));
                    index++;
                }
                continue;
            }
            if (Character.isLowSurrogate(value)) {
                prepared.append('\uFFFD');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.UNPAIRED_SURROGATE, "Unpaired low surrogate", index, 1));
                mappings.add(new MystemOffsetMapping(preparedStart, prepared.length(), index, index + 1));
                index++;
                continue;
            }
            if (isUnsafeControl(value)) {
                prepared.append(' ');
                issues.add(new MystemTextIssue(
                        MystemTextIssueType.CONTROL_CHARACTER, "Control character replaced with space", index, 1));
                mappings.add(new MystemOffsetMapping(preparedStart, prepared.length(), index, index + 1));
                index++;
                continue;
            }
            prepared.append(value);
            mappings.add(new MystemOffsetMapping(preparedStart, prepared.length(), index, index + 1));
            index++;
        }
        return new MystemPreparedText(text, prepared.toString(), mappings, issues);
    }

    private static boolean isUnsafeControl(char value) {
        return Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t';
    }
}
