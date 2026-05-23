package io.github.ulviar.mystem4j.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MystemOffsetAligner {
    private final String originalText;
    private final ArrayList<MystemTextIssue> issues = new ArrayList<>();
    private int cursor;

    MystemOffsetAligner(String originalText) {
        this.originalText = Objects.requireNonNull(originalText, "originalText");
    }

    MystemTextRange align(String tokenText) {
        if (tokenText.isEmpty()) {
            return new MystemTextRange(cursor, cursor);
        }
        int index = originalText.indexOf(tokenText, cursor);
        if (index < 0) {
            issues.add(new MystemTextIssue(
                    MystemTextIssueType.UNMATCHED_TOKEN,
                    "Could not align MyStem token to original text: " + tokenText,
                    cursor,
                    tokenText.length()));
            return MystemTextRange.unknown();
        }
        cursor = index + tokenText.length();
        return new MystemTextRange(index, cursor);
    }

    List<MystemTextIssue> issues() {
        return List.copyOf(issues);
    }
}
