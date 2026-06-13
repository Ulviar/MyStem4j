package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemOptionsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersArgumentsDeterministically() {
        Path fixlist = readableFixlist();
        MystemOptions options = MystemOptions.builder()
                .newLineEachWord(true)
                .copyInput(true)
                .dictionaryWordsOnly(true)
                .lemmaOnly(true)
                .grammarInfo(true)
                .mergeWordForms(true)
                .sentenceMarkers(true)
                .encoding(MystemEncoding.UTF_8)
                .disambiguate(true)
                .englishGrammemes(true)
                .filterGrammar("S")
                .fixlist(fixlist)
                .format(MystemOutputFormat.JSON)
                .generateAll(true)
                .weight(true)
                .build();

        assertEquals(
                List.of(
                        "-n",
                        "-c",
                        "-w",
                        "-l",
                        "-i",
                        "-g",
                        "-s",
                        "-e",
                        "utf-8",
                        "-d",
                        "--eng-gr",
                        "--filter-gram",
                        "S",
                        "--fixlist",
                        fixlist.toString(),
                        "--format",
                        "json",
                        "--generate-all",
                        "--weight"),
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

    @Test
    void rejectsBlankFilterGrammar() {
        assertThrows(
                MystemInvalidOptionsException.class,
                () -> MystemOptions.builder().filterGrammar(" ").build());
    }

    @Test
    void acceptsFixlistPathWithoutTouchingFileSystem() {
        Path missingFixlist = temporaryDirectory.resolve("missing.txt");

        MystemOptions options = MystemOptions.builder().fixlist(missingFixlist).build();

        assertEquals(missingFixlist, options.fixlist().orElseThrow());
    }

    @Test
    void builderRejectsNullValues() {
        MystemOptions.Builder builder = MystemOptions.builder();

        assertThrows(NullPointerException.class, () -> builder.encoding(null));
        assertThrows(NullPointerException.class, () -> builder.filterGrammar(null));
        assertThrows(NullPointerException.class, () -> builder.fixlist(null));
        assertThrows(NullPointerException.class, () -> builder.format(null));
    }

    private Path readableFixlist() {
        try {
            Path fixlist = temporaryDirectory.resolve("fixlist.txt");
            Files.writeString(fixlist, "мама мама\n", StandardCharsets.UTF_8);
            return fixlist;
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }
}
