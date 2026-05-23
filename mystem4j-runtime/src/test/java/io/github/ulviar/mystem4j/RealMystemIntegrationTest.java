package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void probesRealMystem() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));

        MystemProbeResult result = MystemProbe.probe(executable);

        assertTrue(result.output().startsWith("["));
        assertTrue(result.output().contains("\"text\":\"мама\""));
    }

    @Test
    void analyzesTextWithRealMystem() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                .build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertTrue(result.output().startsWith("["));
            assertTrue(result.output().contains("\"text\":\"Мама\""));
            assertTrue(result.output().endsWith(System.lineSeparator()) || result.output().endsWith("\n"));
        }
    }

    @Test
    void analyzesTextWithRealMystemSession() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                .session()
                .build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertTrue(result.output().startsWith("["));
            assertTrue(result.output().contains("\"text\":\"Мама\""));
        }
    }

    @Test
    void analyzesTextWithRealMystemPool() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                .pooled(pool -> pool.maxSize(2).warmupSize(1).minIdle(1))
                .build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertTrue(result.output().startsWith("["));
            assertTrue(result.output().contains("\"text\":\"Мама\""));
        }
    }

    @Test
    void analyzesFileWithRealMystem() throws IOException {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        Path input = temporaryDirectory.resolve("input.txt");
        Path output = temporaryDirectory.resolve("output.json");
        Files.writeString(input, "Мама мыла раму.", StandardCharsets.UTF_8);

        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                .build()) {
            client.analyzeFile(input, output);

            String fileOutput = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(fileOutput.startsWith("["));
            assertTrue(fileOutput.contains("\"text\":\"Мама\""));
        }
    }
}
