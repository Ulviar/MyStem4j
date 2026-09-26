package io.github.ulviar.mystem4j.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.ulviar.mystem4j.*;
import io.github.ulviar.mystem4j.http.MystemHttpClient;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealHttpMystemTest {
    @TempDir Path directory;

    @Test void standaloneConfigurationServesAllModesAndBothFileMethods() throws Exception {
        Path input = directory.resolve("input"); Path output = directory.resolve("output");
        Files.writeString(input, "Кошки спят.\nСобаки едят.");
        for (String mode : List.of("oneshot", "session", "pooled")) {
            try (var server = MystemServerMain.start(Map.of("MYSTEM_EXECUTABLE", System.getProperty("mystem4j.executable"),
                    "MYSTEM_MODE", mode, "MYSTEM_PORT", "0", "MYSTEM_POOL_SIZE", "2", "MYSTEM_SHUTDOWN_GRACE_SECONDS", "0"));
                 var client = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build()) {
                assertTrue(client.analyze("Кошки спят.").output().contains("кошка"));
                if (mode.equals("oneshot")) assertTrue(client.analyze("Кошки\nсобаки").output().contains("собака"));
                else assertThrows(MystemInvalidOptionsException.class, () -> client.analyze("Кошки\nсобаки"));
                assertEquals(MystemExecutionMode.ONE_SHOT_FILE, client.analyzeFile(input).stats().mode());
                assertEquals(MystemExecutionMode.ONE_SHOT_FILE, client.analyzeFile(input, output).stats().mode());
                assertTrue(Files.readString(output).contains("собака"));
                assertTrue(client.analyze("Собаки").output().contains("собака"));
            }
        }
    }

    @Test void luceneTermsAndUtf16OffsetsMatchLocalBackendForAdversarialUnicode() throws Exception {
        var nativeOptions = MystemOptions.builder().grammarInfo(true).copyInput(true).disambiguate(true).build();
        try (var local = Mystem.builder().executable(Path.of(System.getProperty("mystem4j.executable"))).options(nativeOptions).pooled().build();
             var server = MystemServerMain.start(Map.of("MYSTEM_EXECUTABLE", System.getProperty("mystem4j.executable"),
                     "MYSTEM_PORT", "0", "MYSTEM_SHUTDOWN_GRACE_SECONDS", "0"));
             var remote = MystemHttpClient.builder(HttpServiceContractTest.uri(server)).build();
             var localAnalyzer = new MystemLuceneAnalyzer(local); var remoteAnalyzer = new MystemLuceneAnalyzer(remote)) {
            for (String text : List.of("Кошки 🐈 спят.", "е\u0308лки Ёлки\nсобаки", "\ud800Кошки\udfff\u0000 спят", "мама\u2028мыла\tраму")) {
                assertEquals(tokens(localAnalyzer, text), tokens(remoteAnalyzer, text), text);
            }
        }
    }

    private static List<String> tokens(MystemLuceneAnalyzer analyzer, String input) throws Exception {
        var result = new ArrayList<String>();
        try (var stream = analyzer.tokenStream("body", input)) {
            var term = stream.addAttribute(CharTermAttribute.class);
            var offsets = stream.addAttribute(OffsetAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                assertTrue(offsets.startOffset() >= 0 && offsets.endOffset() <= input.length());
                result.add(term.toString() + ":" + offsets.startOffset() + ":" + offsets.endOffset());
            }
            stream.end();
            assertEquals(input.length(), offsets.endOffset());
        }
        return result;
    }
}
