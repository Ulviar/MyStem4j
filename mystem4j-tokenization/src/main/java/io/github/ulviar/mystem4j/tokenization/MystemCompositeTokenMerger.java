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
        return options.mergeUrls() ? mergeUrls(originalText, result, options) : List.copyOf(result);
    }

    private static void flushGroup(
            String originalText, List<MystemPreparedSearchToken> group, List<MystemPreparedSearchToken> result) {
        ArrayList<Entity> entities = new ArrayList<>();
        for (int marker = 0; marker < group.size(); marker++) {
            if (group.get(marker).features.contains(MystemTokenFeature.EMAIL_PART)) {
                MergeRange range = emailRange(originalText, group, marker);
                if (range != null) {
                    String text = originalText.substring(range.startOffset(), range.endOffset());
                    entities.add(new Entity(range, MystemTokenFeature.EMAIL, text.substring(text.lastIndexOf('@') + 1)));
                }
            }
        }
        entities.sort(Comparator.comparingInt(entity -> entity.range().firstIndex()));
        int cursor = 0;
        for (Entity entity : entities) {
            MergeRange range = entity.range();
            if (range.firstIndex() < cursor) {
                continue;
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

    private static List<MystemPreparedSearchToken> mergeUrls(
            String originalText, List<MystemPreparedSearchToken> tokens, MystemSearchTokenizerOptions options) {
        ArrayList<UrlRange> urls = new ArrayList<>();
        // Later entities bound earlier URLs only after comma/semicolon delimiters.
        // Email-like user info, paths and queries remain owned by the URL.
        TreeSet<Integer> boundaries = new TreeSet<>();
        for (MystemPreparedSearchToken token : tokens) {
            if (token.features.contains(MystemTokenFeature.EMAIL)) {
                addUrlBoundary(originalText, token.startOffset, boundaries);
            }
        }
        for (int marker = originalText.indexOf("://"); marker >= 0; ) {
            int start = urlSchemeStart(originalText, marker);
            if (start < 0) {
                marker = originalText.indexOf("://", marker + 3);
                continue;
            }
            Integer boundary = boundaries.higher(marker);
            int spanEnd = urlCandidateEnd(originalText, marker + 3,
                    boundary == null ? originalText.length() : boundary);
            appendUrlsInSpan(originalText, start, marker, spanEnd, urls);
            // The first scheme owns this lexical span even when its URI is invalid.
            // Embedded schemes are path/query content, not fresh suffix candidates.
            marker = originalText.indexOf("://", spanEnd);
        }
        if (urls.isEmpty()) {
            return List.copyOf(tokens);
        }
        ArrayList<MystemPreparedSearchToken> result = new ArrayList<>();
        int index = 0;
        int cursor = 0;
        for (UrlRange url : urls) {
            if (url.startOffset() < cursor) {
                continue;
            }
            while (index < tokens.size() && tokens.get(index).endOffset <= url.startOffset()) {
                MystemPreparedSearchToken token = tokens.get(index++);
                appendFragment(originalText, token, Math.max(cursor, token.startOffset), token.endOffset, result, options);
            }
            if (index < tokens.size() && tokens.get(index).startOffset < url.startOffset()) {
                MystemPreparedSearchToken token = tokens.get(index);
                appendFragment(originalText, token, Math.max(cursor, token.startOffset), url.startOffset(), result, options);
            }
            MystemPreparedSearchToken token = MystemPreparedSearchToken.composite(
                    originalText.substring(url.startOffset(), url.endOffset()),
                    url.startOffset(), url.endOffset(), MystemTokenFeature.URL);
            token.forms.add(url.domain());
            result.add(token);
            cursor = url.endOffset();
            while (index < tokens.size() && tokens.get(index).endOffset <= cursor) {
                index++;
            }
        }
        while (index < tokens.size()) {
            MystemPreparedSearchToken token = tokens.get(index++);
            appendFragment(originalText, token, Math.max(cursor, token.startOffset), token.endOffset, result, options);
        }
        return List.copyOf(result);
    }

    private static void appendUrlsInSpan(
            String text, int start, int marker, int spanEnd, List<UrlRange> urls) {
        ArrayList<UrlCandidate> candidates = new ArrayList<>();
        candidates.add(new UrlCandidate(start, marker, start));
        for (int nextMarker = text.indexOf("://", marker + 3); nextMarker >= 0 && nextMarker < spanEnd;
                nextMarker = text.indexOf("://", nextMarker + 3)) {
            int nextStart = urlSchemeStart(text, nextMarker);
            if (nextStart < 0) {
                continue;
            }
            int delimiter = entityDelimiterStart(text, nextStart);
            if (delimiter < nextStart) {
                candidates.add(new UrlCandidate(nextStart, nextMarker, delimiter));
            }
        }
        ArrayList<RecognizedUrl> recognized = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            UrlCandidate candidate = candidates.get(index);
            int limit = index + 1 < candidates.size() ? candidates.get(index + 1).delimiterOffset() : spanEnd;
            UrlRange range = recognizeUrl(text, candidate, limit);
            if (range != null) {
                recognized.add(new RecognizedUrl(candidate, range, limit));
            }
        }
        // Only recognized adjacent entities form boundaries. A rejected candidate
        // can still be literal content in the preceding URL's path or query.
        // Both passes validate disjoint ranges, so URI parsing never revisits a
        // growing sequence of nested suffixes, including malformed tails.
        for (int index = 0; index < recognized.size(); index++) {
            RecognizedUrl url = recognized.get(index);
            int limit = index + 1 < recognized.size()
                    ? recognized.get(index + 1).candidate().delimiterOffset() : spanEnd;
            UrlRange extended = limit > url.limit() ? recognizeUrl(text, url.candidate(), limit) : null;
            urls.add(extended == null ? url.range() : extended);
        }
    }

    private static UrlRange recognizeUrl(String text, UrlCandidate candidate, int limit) {
        int end = urlCandidateEnd(text, candidate.markerOffset() + 3, limit);
        while (end > candidate.markerOffset() + 3 && ".,;!'".indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        String domain = MystemEntitySyntax.urlDomain(text.substring(candidate.startOffset(), end));
        if (domain == null) {
            // A comma after a bare authority may introduce prose. Try that one
            // boundary; repeatedly validating longer malformed prefixes is unsafe.
            for (int cursor = candidate.markerOffset() + 3; cursor < end; cursor++) {
                if (isEntityDelimiter(text.charAt(cursor))) {
                    domain = MystemEntitySyntax.urlDomain(text.substring(candidate.startOffset(), cursor));
                    end = cursor;
                    break;
                }
            }
        }
        return domain == null ? null : new UrlRange(candidate.startOffset(), end, domain);
    }

    private static int urlSchemeStart(String text, int marker) {
        int start = marker;
        while (start > 0 && isUriSchemePart(text.charAt(start - 1))) {
            start--;
        }
        return isUriScheme(text.substring(start, marker)) ? start : -1;
    }

    private static void appendFragment(
            String originalText, MystemPreparedSearchToken token, int start, int end,
            List<MystemPreparedSearchToken> result, MystemSearchTokenizerOptions options) {
        if (start < end) {
            result.add(start == token.startOffset && end == token.endOffset ? token
                    : MystemPreparedSearchToken.gap(originalText.substring(start, end), start, end, options));
        }
    }

    private static void addUrlBoundary(String text, int start, TreeSet<Integer> boundaries) {
        int delimiter = entityDelimiterStart(text, start);
        if (delimiter < start) {
            boundaries.add(delimiter);
        }
    }

    private static int entityDelimiterStart(String text, int start) {
        int delimiter = start;
        while (delimiter > 0 && isEntityDelimiter(text.charAt(delimiter - 1))) {
            delimiter--;
        }
        return delimiter;
    }

    private static int urlCandidateEnd(String text, int start, int limit) {
        int parentheses = 0;
        int brackets = 0;
        int cursor = start;
        while (cursor < limit) {
            int codePoint = text.codePointAt(cursor);
            if (codePoint == '(') {
                parentheses++;
            } else if (codePoint == '[') {
                brackets++;
            } else if (codePoint == ')') {
                if (parentheses-- == 0) {
                    break;
                }
            } else if (codePoint == ']') {
                if (brackets-- == 0) {
                    break;
                }
            } else if (MystemSearchTokenClassifier.isSeparator(codePoint)
                    || codePoint == '<' || codePoint == '>') {
                break;
            }
            cursor += Character.charCount(codePoint);
        }
        return cursor;
    }

    private static boolean isEntityDelimiter(char character) {
        return character == ',' || character == ';';
    }

    private static boolean isUriSchemePart(char character) {
        return isAsciiLetter(character) || (character >= '0' && character <= '9')
                || character == '+' || character == '-' || character == '.';
    }

    private static boolean isUriScheme(String text) {
        if (text.isEmpty() || !isAsciiLetter(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!isUriSchemePart(character)) {
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

    private record UrlCandidate(int startOffset, int markerOffset, int delimiterOffset) {}

    private record RecognizedUrl(UrlCandidate candidate, UrlRange range, int limit) {}

    private record UrlRange(int startOffset, int endOffset, String domain) {}

    private record Entity(MergeRange range, MystemTokenFeature feature, String domain) {}

    private record MergeRange(int firstIndex, int lastIndex, int startOffset, int endOffset) {}
}
