package io.github.ulviar.mystem4j.tokenization;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;

/** Syntactic entity recognition; never resolves hosts or consults a TLD registry. */
final class MystemEntitySyntax {
    private static final String EMAIL_LOCAL_PUNCTUATION = "!#$%&'*+-/=?^_`{|}~.";

    private MystemEntitySyntax() {}

    static String urlDomain(String text) {
        try {
            URI uri = new URI(text);
            String authority = uri.getRawAuthority();
            if (uri.getScheme() == null || authority == null || authority.isEmpty()) {
                return null;
            }
            int at = authority.lastIndexOf('@');
            if (at >= 0 && authority.indexOf('@') != at) {
                return null;
            }
            String hostAndPort = authority.substring(at + 1);
            String host;
            int portStart;
            if (hostAndPort.startsWith("[")) {
                int bracket = hostAndPort.indexOf(']');
                if (bracket < 0 || uri.getHost() == null) {
                    return null;
                }
                host = hostAndPort.substring(0, bracket + 1);
                portStart = bracket + 1;
            } else {
                int colon = hostAndPort.indexOf(':');
                portStart = colon < 0 ? hostAndPort.length() : colon;
                host = hostAndPort.substring(0, portStart);
                if (!validHost(host)) {
                    return null;
                }
            }
            if (portStart < hostAndPort.length()
                    && (hostAndPort.charAt(portStart) != ':' || !validPort(hostAndPort.substring(portStart + 1)))) {
                return null;
            }
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            return host.regionMatches(true, 0, "www.", 0, 4) ? host.substring(4) : host;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    static boolean isEmail(String text) {
        int at = text.indexOf('@');
        if (at <= 0 || at != text.lastIndexOf('@')) {
            return false;
        }
        String local = text.substring(0, at);
        String host = text.substring(at + 1);
        return isEmailLocalPart(local) && !local.startsWith(".") && !local.endsWith(".") && !local.contains("..")
                && host.indexOf('.') > 0 && !host.endsWith(".") && validHost(host);
    }

    static boolean isEmailLocalPart(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(character -> isLetterDigitOrMark(character)
                || EMAIL_LOCAL_PUNCTUATION.indexOf(character) >= 0);
    }

    static boolean isEmailHostPart(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(MystemEntitySyntax::isHostCharacter);
    }

    static boolean isHostCharacter(int character) {
        return isLetterDigitOrMark(character) || character == '-' || character == '.';
    }

    private static boolean isLetterDigitOrMark(int character) {
        int type = Character.getType(character);
        return Character.isLetterOrDigit(character) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static boolean validHost(String host) {
        if (host.isEmpty() || !isEmailHostPart(host)) {
            return false;
        }
        try {
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
            return !ascii.isEmpty() && !ascii.equals(".")
                    && ascii.length() <= (ascii.endsWith(".") ? 254 : 253);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean validPort(String text) {
        if (text.isEmpty()) {
            return false;
        }
        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            value = value * 10 + character - '0';
            if (value > 65535) {
                return false;
            }
        }
        return true;
    }
}
