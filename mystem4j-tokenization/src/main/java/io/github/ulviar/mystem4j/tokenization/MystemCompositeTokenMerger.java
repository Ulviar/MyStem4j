package io.github.ulviar.mystem4j.tokenization;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class MystemCompositeTokenMerger {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}](?:[\\p{L}\\p{N}\\-]*[\\p{L}\\p{N}])?(?:\\.[\\p{L}\\p{N}](?:[\\p{L}\\p{N}\\-]*[\\p{L}\\p{N}])?)+$");
    private static final Pattern EMAIL_PUNCTUATION = Pattern.compile("[._%+\\-]+");

    private MystemCompositeTokenMerger() {}

    static List<MystemPreparedSearchToken> merge(
            String originalText, List<MystemPreparedSearchToken> tokens, MystemSearchTokenizerOptions options) {
        if (!options.mergeUrls() && !options.mergeEmails()) {
            return List.copyOf(tokens);
        }
        ArrayList<MystemPreparedSearchToken> result = new ArrayList<>();
        ArrayList<MystemPreparedSearchToken> group = new ArrayList<>();
        boolean possibleUrl = false;
        boolean possibleEmail = false;
        for (MystemPreparedSearchToken token : tokens) {
            possibleUrl =
                    possibleUrl || (options.mergeUrls() && token.features.contains(MystemTokenFeature.URL_PART));
            possibleEmail =
                    possibleEmail || (options.mergeEmails() && token.features.contains(MystemTokenFeature.EMAIL_PART));
            if (token.features.contains(MystemTokenFeature.SEPARATOR)) {
                flushGroup(originalText, group, possibleUrl, possibleEmail, result);
                group.clear();
                possibleUrl = false;
                possibleEmail = false;
                result.add(token);
            } else {
                group.add(token);
            }
        }
        flushGroup(originalText, group, possibleUrl, possibleEmail, result);
        return List.copyOf(result);
    }

    private static void flushGroup(
            String originalText,
            List<MystemPreparedSearchToken> group,
            boolean possibleUrl,
            boolean possibleEmail,
            List<MystemPreparedSearchToken> result) {
        if (group.isEmpty()) {
            return;
        }
        if (possibleUrl == possibleEmail) {
            result.addAll(group);
            return;
        }
        List<MystemPreparedSearchToken> merged =
                possibleUrl ? mergeUrl(originalText, group) : mergeEmail(originalText, group);
        if (merged.isEmpty()) {
            result.addAll(group);
        } else {
            result.addAll(merged);
        }
    }

    private static List<MystemPreparedSearchToken> mergeUrl(
            String originalText, List<MystemPreparedSearchToken> group) {
        MergeRange range = urlRange(originalText, group);
        if (range == null) {
            return List.of();
        }
        String text = originalText.substring(range.startOffset(), range.endOffset());
        String host = urlHost(text);
        if (host == null) {
            return List.of();
        }
        MystemPreparedSearchToken token = MystemPreparedSearchToken.composite(
                text, range.startOffset(), range.endOffset(), MystemTokenFeature.URL);
        token.forms.add(host);
        return mergedGroup(group, range, token);
    }

    private static MergeRange urlRange(String originalText, List<MystemPreparedSearchToken> group) {
        for (int marker = 0; marker < group.size(); marker++) {
            if (!group.get(marker).features.contains(MystemTokenFeature.URL_PART) || marker == 0) {
                continue;
            }
            int first = marker - 1;
            MystemPreparedSearchToken scheme = group.get(first);
            if (!scheme.features.contains(MystemTokenFeature.WORD) || !isUriScheme(scheme.text)) {
                continue;
            }
            for (int last = group.size() - 1; last > marker; last--) {
                MystemPreparedSearchToken candidateEnd = group.get(last);
                if (!candidateEnd.features.contains(MystemTokenFeature.WORD)
                        && !candidateEnd.features.contains(MystemTokenFeature.NUMBER)) {
                    continue;
                }
                MergeRange range = new MergeRange(first, last, scheme.startOffset, candidateEnd.endOffset);
                String candidate = originalText.substring(range.startOffset(), range.endOffset());
                if (urlHost(candidate) != null) {
                    return range;
                }
            }
        }
        return null;
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

    private static List<MystemPreparedSearchToken> mergeEmail(
            String originalText, List<MystemPreparedSearchToken> group) {
        MergeRange range = emailRange(group);
        if (range == null) {
            return List.of();
        }
        String text = originalText.substring(range.startOffset(), range.endOffset());
        if (!EMAIL_PATTERN.matcher(text).matches()) {
            return List.of();
        }
        String domain = text.substring(text.lastIndexOf('@') + 1);
        MystemPreparedSearchToken token = MystemPreparedSearchToken.composite(
                text, range.startOffset(), range.endOffset(), MystemTokenFeature.EMAIL);
        token.forms.add(domain);
        return mergedGroup(group, range, token);
    }

    private static MergeRange emailRange(List<MystemPreparedSearchToken> group) {
        for (int index = 0; index < group.size(); index++) {
            if (!group.get(index).features.contains(MystemTokenFeature.EMAIL_PART)) {
                continue;
            }
            int first = index - 1;
            while (first >= 0 && isEmailAtom(group.get(first))) {
                first--;
            }
            first++;
            int last = index + 1;
            while (last < group.size() && isEmailAtom(group.get(last))) {
                last++;
            }
            last--;
            while (first < index && isEmailPunctuation(group.get(first))) {
                first++;
            }
            while (last > index && isEmailPunctuation(group.get(last))) {
                last--;
            }
            if (first < index && last > index) {
                return new MergeRange(first, last, group.get(first).startOffset, group.get(last).endOffset);
            }
        }
        return null;
    }

    private static boolean isEmailAtom(MystemPreparedSearchToken token) {
        return token.features.contains(MystemTokenFeature.WORD)
                || token.features.contains(MystemTokenFeature.NUMBER)
                || isEmailPunctuation(token);
    }

    private static boolean isEmailPunctuation(MystemPreparedSearchToken token) {
        return EMAIL_PUNCTUATION.matcher(token.text).matches();
    }

    private static List<MystemPreparedSearchToken> mergedGroup(
            List<MystemPreparedSearchToken> group, MergeRange range, MystemPreparedSearchToken token) {
        ArrayList<MystemPreparedSearchToken> result =
                new ArrayList<>(group.size() - range.lastIndex() + range.firstIndex() + 1);
        result.addAll(group.subList(0, range.firstIndex()));
        result.add(token);
        result.addAll(group.subList(range.lastIndex() + 1, group.size()));
        return List.copyOf(result);
    }

    private static String urlHost(String text) {
        try {
            URI uri = new URI(text);
            String host = uri.getHost();
            return host == null || host.isBlank() ? null : host;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private record MergeRange(int firstIndex, int lastIndex, int startOffset, int endOffset) {}
}
