package io.github.ulviar.mystem4j.model;

record MystemTextRange(int startOffset, int endOffset) {
    static MystemTextRange unknown() {
        return new MystemTextRange(-1, -1);
    }
}
