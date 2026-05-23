package io.github.ulviar.mystem4j;

final class MystemProbeValidator {
    static final String SMOKE_TEXT = "мама";

    private MystemProbeValidator() {}

    static void validateJsonSmokeOutput(String output) {
        if (output == null || output.isBlank()) {
            throw new MystemProtocolException("MyStem probe produced empty output.", null);
        }
        String compact = output.trim();
        if (!compact.startsWith("[") || !compact.endsWith("]") || !compact.contains("\"text\":\"" + SMOKE_TEXT + "\"")) {
            throw new MystemProtocolException("MyStem probe output does not look like MyStem JSON: " + trim(compact), null);
        }
    }

    private static String trim(String text) {
        if (text.length() <= 2_000) {
            return text;
        }
        return text.substring(0, 2_000);
    }
}
