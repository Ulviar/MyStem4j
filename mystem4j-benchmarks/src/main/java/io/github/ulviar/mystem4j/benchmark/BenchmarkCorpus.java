package io.github.ulviar.mystem4j.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Authored workload fixtures; external UTF-8 documents can replace them for application measurements. */
final class BenchmarkCorpus {
    private static final List<String> BUILTIN = List.of("prose.txt", "technical.txt", "messages.txt", "queries.txt");

    private BenchmarkCorpus() {}

    static List<String> documents(int targetChars, String directory) throws IOException {
        if (targetChars < 1) {
            throw new IllegalArgumentException("targetChars must be positive");
        }
        ArrayList<String> source = new ArrayList<>();
        if (directory == null || directory.isBlank()) {
            for (String name : BUILTIN) {
                try (var input = BenchmarkCorpus.class.getResourceAsStream("/corpus/" + name)) {
                    if (input == null) {
                        throw new IOException("Missing benchmark fixture: " + name);
                    }
                    source.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } else {
            try (var paths = Files.list(Path.of(directory))) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".txt")).sorted().toList()) {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    if (text.isBlank()) {
                        throw new IOException("Empty benchmark document: " + path);
                    }
                    source.add(text);
                }
            }
            if (source.isEmpty()) {
                throw new IOException("Corpus directory must contain nonempty UTF-8 .txt files: " + directory);
            }
        }
        return source.stream().map(text -> resize(text, targetChars)).toList();
    }

    private static String resize(String source, int targetChars) {
        StringBuilder text = new StringBuilder(targetChars + source.length());
        while (text.length() <= targetChars) {
            text.append(source).append('\n');
        }
        if (Character.isHighSurrogate(text.charAt(targetChars - 1)) && Character.isLowSurrogate(text.charAt(targetChars))) {
            return text.substring(0, targetChars - 1) + " ";
        }
        return text.substring(0, targetChars);
    }

    static String json(String text) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < text.length(); ) {
            int cp = text.codePointAt(index);
            if (!wordPart(cp)) {
                index += Character.charCount(cp);
                continue;
            }
            StringBuilder surface = new StringBuilder();
            while (index < text.length() && wordPart(text.codePointAt(index))) {
                cp = text.codePointAt(index);
                if (cp != 0x00AD) {
                    surface.appendCodePoint(cp);
                }
                index += Character.charCount(cp);
            }
            if (surface.isEmpty()) {
                continue;
            }
            if (json.length() > 1) {
                json.append(',');
            }
            String lemma = switch (surface.toString()) {
                case "Мамы" -> "мама";
                case "любят" -> "любить";
                case "пишут" -> "писать";
                case "Один" -> "один";
                default -> "";
            };
            json.append("{\"text\":\"").append(surface).append("\",\"analysis\":[");
            if (!lemma.isEmpty()) {
                json.append("{\"lex\":\"").append(lemma).append("\",\"gr\":\"S,ед=им\"}");
            }
            json.append("]}");
        }
        return json.append(']').toString();
    }

    private static boolean wordPart(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == 0x00AD;
    }
}
