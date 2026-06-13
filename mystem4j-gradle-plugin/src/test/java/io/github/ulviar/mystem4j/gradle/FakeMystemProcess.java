package io.github.ulviar.mystem4j.gradle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class FakeMystemProcess {
    private FakeMystemProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("mode is required");
        }
        switch (args[0]) {
            case "echo" -> echoJsonLines();
            case "fail" -> fail(Integer.parseInt(args[1]), args[2]);
            case "largeOutput" -> System.out.print("0123456789");
            case "notMystem" -> System.out.println("ready");
            default -> throw new IllegalArgumentException("unknown fake MyStem mode: " + args[0]);
        }
    }

    private static void echoJsonLines() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String input;
            while ((input = reader.readLine()) != null) {
                writer.print("[{\"text\":\"" + escapeJson(input) + "\"}]\n");
                writer.flush();
            }
        }
    }

    private static void fail(int exitCode, String stderr) {
        System.err.println(stderr);
        System.exit(exitCode);
    }

    private static String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            switch (value) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(value);
            }
        }
        return escaped.toString();
    }
}
