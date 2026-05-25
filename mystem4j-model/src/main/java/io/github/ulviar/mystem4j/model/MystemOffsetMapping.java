package io.github.ulviar.mystem4j.model;

record MystemOffsetMapping(int preparedStart, int preparedEnd, int originalStart, int originalEnd) {
    MystemOffsetMapping {
        if (preparedStart < 0 || preparedEnd < preparedStart || originalStart < 0 || originalEnd < originalStart) {
            throw new IllegalArgumentException("invalid offset mapping");
        }
    }
}
