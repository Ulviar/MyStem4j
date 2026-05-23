package io.github.ulviar.mystem4j.model;

/**
 * Mapping from a range in prepared text back to a range in the original text.
 */
public record MystemOffsetMapping(int preparedStart, int preparedEnd, int originalStart, int originalEnd) {
    public MystemOffsetMapping {
        if (preparedStart < 0 || preparedEnd < preparedStart || originalStart < 0 || originalEnd < originalStart) {
            throw new IllegalArgumentException("invalid offset mapping");
        }
    }
}
