package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MystemOptionsTest {
    @Test
    void rendersArgumentsDeterministically() {
        MystemOptions options = MystemOptions.builder()
                .newLineEachWord(true)
                .copyInput(true)
                .grammarInfo(true)
                .mergeWordForms(true)
                .sentenceMarkers(true)
                .encoding(MystemEncoding.UTF_8)
                .disambiguate(true)
                .englishGrammemes(true)
                .format(MystemOutputFormat.JSON)
                .weight(true)
                .build();

        assertEquals(
                List.of("-n", "-c", "-i", "-g", "-s", "-e", "utf-8", "-d", "--eng-gr", "--format", "json", "--weight"),
                options.toArguments());
    }

    @Test
    void rejectsMergeWithoutGrammarInfo() {
        assertThrows(
                MystemInvalidOptionsException.class,
                () -> MystemOptions.builder().mergeWordForms(true).build());
    }

    @Test
    void rejectsSentenceMarkersWithoutCopyInput() {
        assertThrows(
                MystemInvalidOptionsException.class,
                () -> MystemOptions.builder().sentenceMarkers(true).build());
    }
}
