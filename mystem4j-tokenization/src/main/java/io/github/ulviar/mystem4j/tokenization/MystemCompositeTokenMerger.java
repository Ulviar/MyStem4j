package io.github.ulviar.mystem4j.tokenization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

final class MystemCompositeTokenMerger {
    private MystemCompositeTokenMerger() {}

    static List<MystemPreparedSearchToken> merge(
            String originalText, List<MystemPreparedSearchToken> tokens, MystemSearchTokenizerOptions options) {
        if (!options.mergeUrls() && !options.mergeEmails()) {
            return List.copyOf(tokens);
        }
        ArrayList<MystemPreparedSearchToken> result = new ArrayList<>();
        ArrayList<MystemPreparedSearchToken> group = new ArrayList<>();
        for (MystemPreparedSearchToken token : tokens) {
            if (token.features.contains(MystemTokenFeature.SEPARATOR)
                    && !(options.mergeEmails() && MystemEntitySyntax.isEmailLocalPart(token.text))) {
                flushGroup(originalText, group, result);
                group.clear();
                result.add(token);
            } else {
                group.add(token);
            }
        }
        flushGroup(originalText, group, result);
        return List.copyOf(result);
    }

    private static void flushGroup(
            String originalText, List<MystemPreparedSearchToken> group, List<MystemPreparedSearchToken> result) {
        ArrayList<Entity> entities = new ArrayList<>();
        TreeSet<Integer> boundaries = new TreeSet<>();
        for (int marker = 0; marker < group.size(); marker++) {
            if (group.get(marker).features.contains(MystemTokenFeature.EMAIL_PART)) {
                MergeRange range = emailRange(originalText, group, marker);
                if (range != null) {
                    String text = originalText.substring(range.startOffset(), range.endOffset());
                    entities.add(new Entity(range, MystemTokenFeature.EMAIL, text.substring(text.lastIndexOf('@') + 1)));
                    addBoundary(group, range, boundaries);
                }
            }
        }
        // Work backwards so a later valid entity can bound the preceding URL.
        // Ordinary commas/semicolons in a URL remain part of that URL.
        for (int marker = group.size() - 1; marker > 0; marker--) {
            if (!group.get(marker).features.contains(MystemTokenFeature.URL_PART)) {
                continue;
            }
            Integer boundary = boundaries.higher(marker);
            int limit = boundary == null ? group.size() : boundary;
            MergeRange range = urlRange(originalText, group, marker, limit);
            if (range != null) {
                entities.add(new Entity(range, MystemTokenFeature.URL,
                        MystemEntitySyntax.urlDomain(originalText.substring(range.startOffset(), range.endOffset()))));
                addBoundary(group, range, boundaries);
            }
        }
        entities.sort(Comparator.comparingInt(entity -> entity.range().firstIndex()));
        int cursor = 0;
        for (Entity entity : entities) {
            MergeRange range = entity.range();
            if (range.firstIndex() < cursor) {
                continue; // A URL owns an email-like range in its user info, path or query.
            }
            result.addAll(group.subList(cursor, range.firstIndex()));
            MystemPreparedSearchToken token = MystemPreparedSearchToken.composite(
                    originalText.substring(range.startOffset(), range.endOffset()),
                    range.startOffset(), range.endOffset(), entity.feature());
            token.forms.add(entity.domain());
            result.add(token);
            cursor = range.lastIndex() + 1;
        }
        result.addAll(group.subList(cursor, group.size()));
    }

    private static boolean isEntityDelimiter(String text) {
        return !text.isEmpty() && text.chars().allMatch(character -> character == ',' || character == ';');
    }

    private static void addBoundary(List<MystemPreparedSearchToken> group, MergeRange range, TreeSet<Integer> boundaries) {
        int delimiter = range.firstIndex() - 1;
        if (delimiter >= 0 && isEntityDelimiter(group.get(delimiter).text)) {
            boundaries.add(delimiter);
        }
    }

    private static MergeRange urlRange(
            String originalText, List<MystemPreparedSearchToken> group, int marker, int limit) {
        MystemPreparedSearchToken scheme = group.get(marker - 1);
        if (!scheme.features.contains(MystemTokenFeature.WORD) || !isUriScheme(scheme.text)) {
            return null;
        }
        for (int last = limit - 1; last > marker; last--) {
            MystemPreparedSearchToken candidateEnd = group.get(last);
            if (!candidateEnd.features.contains(MystemTokenFeature.WORD)
                    && !candidateEnd.features.contains(MystemTokenFeature.NUMBER)
                    && !isUrlTail(candidateEnd.text)) {
                continue;
            }
            MergeRange range = new MergeRange(marker - 1, last, scheme.startOffset, candidateEnd.endOffset);
            if (canEndUrl(originalText, range.endOffset(), group.get(limit - 1).endOffset)
                    && MystemEntitySyntax.urlDomain(originalText.substring(range.startOffset(), range.endOffset())) != null) {
                return range;
            }
        }
        return null;
    }

    private static boolean isUrlTail(String text) {
        return !text.isEmpty() && text.chars().allMatch(character -> "/?#=&".indexOf(character) >= 0);
    }

    private static boolean canEndUrl(String originalText, int endOffset, int limit) {
        if (endOffset >= limit) {
            return true;
        }
        int next = originalText.codePointAt(endOffset);
        if (next == ',' || next == ';') {
            return true;
        }
        // Never salvage a malformed authority by cutting before its port, host label,
        // or path. Doing so would index a different URL from the value in the source.
        for (int cursor = endOffset; cursor < limit; cursor++) {
            if (".,;!'".indexOf(originalText.charAt(cursor)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUriScheme(String text) {
        if (text.isEmpty() || !isAsciiLetter(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!isAsciiLetter(character)
                    && !Character.isDigit(character)
                    && character != '+'
                    && character != '-'
                    && character != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
    }

    private static MergeRange emailRange(String originalText, List<MystemPreparedSearchToken> group, int marker) {
        int first = marker - 1;
        while (first >= 0 && MystemEntitySyntax.isEmailLocalPart(sourceText(originalText, group.get(first)))) {
            first--;
        }
        first++;
        int last = marker + 1;
        while (last < group.size() && MystemEntitySyntax.isEmailHostPart(sourceText(originalText, group.get(last)))) {
            last++;
        }
        last--;
        while (last > marker && group.get(last).text.chars().allMatch(character -> character == '.')) {
            last--;
        }
        if (first >= marker || last <= marker) {
            return null;
        }
        int unwrappedFirst = unwrapEmailStart(originalText, group, first, marker, last);
        boolean wrapped = unwrappedFirst != first;
        first = unwrappedFirst;
        MergeRange range = new MergeRange(first, last, group.get(first).startOffset, group.get(last).endOffset);
        if (first > 0 && group.get(first - 1).text.codePoints().anyMatch(character -> character == '@' || character == '\\')) {
            return null;
        }
        if (!wrapped && !canEndEmail(originalText, range.endOffset(), group.getLast().endOffset)) {
            return null;
        }
        return MystemEntitySyntax.isEmail(originalText.substring(range.startOffset(), range.endOffset())) ? range : null;
    }

    private static int unwrapEmailStart(
            String originalText, List<MystemPreparedSearchToken> group, int first, int marker, int last) {
        int afterWrappers = first;
        StringBuilder closers = new StringBuilder();
        while (afterWrappers < marker) {
            String text = sourceText(originalText, group.get(afterWrappers));
            if (!text.chars().allMatch(character -> character == '\'' || character == '{')) {
                break;
            }
            for (int index = 0; index < text.length(); index++) {
                closers.append(text.charAt(index) == '{' ? '}' : '\'');
            }
            afterWrappers++;
        }
        // Apostrophes and braces are valid local-part characters. Treat them as
        // surrounding punctuation only when the entire opening sequence is paired.
        return afterWrappers > first && afterWrappers < marker
                        && originalText.startsWith(closers.reverse().toString(), group.get(last).endOffset)
                ? afterWrappers : first;
    }

    private static boolean canEndEmail(String originalText, int endOffset, int limit) {
        for (int cursor = endOffset; cursor < limit; cursor++) {
            char character = originalText.charAt(cursor);
            if (character == ',' || character == ';' || character == '}') {
                return true;
            }
            if (".!?:'".indexOf(character) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String sourceText(String originalText, MystemPreparedSearchToken token) {
        return originalText.substring(token.startOffset, token.endOffset);
    }

    private record Entity(MergeRange range, MystemTokenFeature feature, String domain) {}

    private record MergeRange(int firstIndex, int lastIndex, int startOffset, int endOffset) {}
}
