package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "mystem4j.executable", matches = ".+")
class RealMystemIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"oneshot", "session", "pooled"})
    void treatsRelativeFileNamesStartingWithDashAsPaths(String mode) throws IOException {
        Path directory = Files.createTempDirectory(Path.of(""), "-mystem-files-");
        Path input = directory.resolve("input.txt");
        Path output = directory.resolve("output.json");
        try {
            Files.writeString(input, "Мама мыла раму.", StandardCharsets.UTF_8);
            var builder = Mystem.builder().executable(Path.of(System.getProperty("mystem4j.executable")));
            if (mode.equals("session")) builder.session();
            if (mode.equals("pooled")) builder.pooled();
            try (var client = builder.build()) {
                var captured = client.analyzeFile(input);
                assertEquals(input, captured.input());
                assertTrue(captured.output().contains("\"lex\":\"мама\""));
                // An absolute input isolates the output-argument case.
                var saved = client.analyzeFile(input.toAbsolutePath(), output);
                assertEquals(input.toAbsolutePath(), saved.input());
                assertEquals(output, saved.output());
                assertTrue(Files.readString(output).contains("\"lex\":\"мама\""));
                assertTrue(saved.stats().outputBytes() > 0);
                assertEquals("Мама мыла раму.", Files.readString(input));
            }
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

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
            assertEquals(1, result.output().lines().count());
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
    void analyzesConcurrentRequestsWithRealMystemPool() throws Exception {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        try (MystemClient client = Mystem.builder()
                        .executable(executable)
                        .options(MystemOptions.builder().format(MystemOutputFormat.JSON).build())
                        .pooled(pool -> pool.maxSize(3).warmupSize(1).minIdle(1))
                        .build();
                var executor = Executors.newFixedThreadPool(3)) {
            ArrayList<Callable<MystemRawResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                int value = index;
                tasks.add(() -> client.analyze("Мама " + value));
            }

            List<Future<MystemRawResult>> futures = executor.invokeAll(tasks);

            for (int index = 0; index < futures.size(); index++) {
                String output = futures.get(index).get().output();
                assertTrue(output.startsWith("["));
                assertTrue(output.contains("\"text\":\"Мама\""));
            }
        }
    }

    @Test
    void longRequestsMakeProgressForEveryCallerInASingleWorkerPool() throws Exception {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        String text = "Мамы любят книги. Разработчики пишут программы и обсуждают поиск. ".repeat(256);
        CountDownLatch start = new CountDownLatch(1);
        try (MystemClient client = Mystem.builder().executable(executable)
                .options(MystemOptions.builder().grammarInfo(true).disambiguate(true).build())
                .requestTimeout(Duration.ofSeconds(10))
                .pooled(pool -> pool.maxSize(1).warmupSize(1).acquireTimeout(Duration.ofSeconds(10)))
                .build(); var executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> calls = new ArrayList<>();
            for (int caller = 0; caller < 4; caller++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    for (int request = 0; request < 20; request++) {
                        assertTrue(client.analyze(text).output().contains("\"text\":\"Мамы\""));
                    }
                    return 20;
                }));
            }
            start.countDown();
            for (Future<Integer> call : calls) {
                assertEquals(20, call.get(60, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void supportsGrammarDisambiguationEnglishGrammemesAndWeightsWithRealMystem() {
        Path executable = Path.of(System.getProperty("mystem4j.executable"));
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .disambiguate(true)
                        .englishGrammemes(true)
                        .weight(true)
                        .build())
                .build()) {
            MystemRawResult result = client.analyze("Мама мыла раму.");

            assertTrue(result.output().contains("\"gr\""));
            assertTrue(result.output().contains("\"wt\""));
            assertTrue(result.output().contains("S,"));
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
