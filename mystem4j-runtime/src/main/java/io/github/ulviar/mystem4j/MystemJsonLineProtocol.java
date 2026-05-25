package io.github.ulviar.mystem4j;

final class MystemJsonLineProtocol {
    private MystemJsonLineProtocol() {}

    static void validateRequest(String text) {
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new MystemInvalidOptionsException("Reusable MyStem JSON line protocol rejects multiline input.");
        }
    }
}
