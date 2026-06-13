package io.github.ulviar.mystem4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public final class FakeMystemProcess {
    private static final Charset CP1251 = Charset.forName("windows-1251");

    private FakeMystemProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("mode is required");
        }
        String mode = args[0];
        String[] modeArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (mode) {
            case "echo" -> oneShotEcho(modeArgs);
            case "cp1251Echo" -> cp1251Echo();
            case "fail" -> fail(Integer.parseInt(modeArgs[0]), modeArgs[1]);
            case "largeOutput" -> System.out.print("0123456789abcdefghijklmnopqrstuvwxyz");
            case "notMystem" -> System.out.println("ready");
            case "sleep" -> sleep(Long.MAX_VALUE);
            case "interactiveEcho" -> interactiveEcho();
            case "exitOnFirstRequest" -> exitOnFirstRequest();
            case "slowInteractive" -> slowInteractive();
            case "noisyInteractive" -> noisyInteractive();
            case "pidInteractive" -> pidInteractive();
            case "crashOnDie" -> crashOnDie();
            case "recordPidInteractive" -> recordPidAndRun(modeArgs[0], FakeMystemProcess::interactiveEcho);
            case "recordPidSleep" -> recordPidAndRun(modeArgs[0], () -> sleep(Long.MAX_VALUE));
            default -> throw new IllegalArgumentException("unknown fake MyStem mode: " + mode);
        }
    }

    private static void oneShotEcho(String[] args) throws IOException {
        List<String> arguments = List.of(args);
        if (copyInputToOutputFile(arguments)) {
            return;
        }
        if (copyInputFileToStdout(arguments)) {
            return;
        }
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        System.out.print(jsonLine(input));
    }

    private static boolean copyInputToOutputFile(List<String> arguments) throws IOException {
        if (arguments.size() < 2) {
            return false;
        }
        Path input = Path.of(arguments.get(arguments.size() - 2));
        Path output = Path.of(arguments.getLast());
        if (!Files.isRegularFile(input)) {
            return false;
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }
        Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static boolean copyInputFileToStdout(List<String> arguments) throws IOException {
        if (arguments.isEmpty()) {
            return false;
        }
        Path input = Path.of(arguments.getLast());
        if (!Files.isRegularFile(input)) {
            return false;
        }
        try (OutputStream output = System.out) {
            Files.copy(input, output);
        }
        return true;
    }

    private static void cp1251Echo() throws IOException {
        byte[] input = System.in.readAllBytes();
        byte[] expected = "Привет".getBytes(CP1251);
        if (!Arrays.equals(input, expected)) {
            System.err.println("unexpected input bytes: " + java.util.HexFormat.of().formatHex(input));
            System.exit(7);
        }
        System.out.write(jsonLine("Привет").getBytes(CP1251));
    }

    private static void fail(int exitCode, String stderr) {
        System.err.println(stderr);
        System.exit(exitCode);
    }

    private static void interactiveEcho() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String input;
            while ((input = reader.readLine()) != null) {
                writer.print(jsonLine(input));
                writer.flush();
            }
        }
    }

    private static void exitOnFirstRequest() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            if (reader.readLine() != null) {
                System.err.println("fatal mystem");
                System.exit(9);
            }
        }
    }

    private static void slowInteractive() throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String input;
            while ((input = reader.readLine()) != null) {
                Thread.sleep(1_000L);
                writer.print(jsonLine(input));
                writer.flush();
            }
        }
    }

    private static void noisyInteractive() throws IOException, InterruptedException {
        byte[] noise = "stderr-noise".repeat(256).getBytes(StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String input;
            while ((input = reader.readLine()) != null) {
                System.err.write(noise);
                System.err.flush();
                Thread.sleep(200L);
                writer.print(jsonLine(input));
                writer.flush();
            }
        }
    }

    private static void pidInteractive() throws IOException {
        long pid = ProcessHandle.current().pid();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            while (reader.readLine() != null) {
                writer.print(jsonLine("pid:" + pid));
                writer.flush();
            }
        }
    }

    private static void crashOnDie() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String input;
            while ((input = reader.readLine()) != null) {
                if ("die".equals(input)) {
                    System.err.println("worker died");
                    System.exit(12);
                }
                writer.print(jsonLine(input));
                writer.flush();
            }
        }
    }

    private static void recordPidAndRun(String pidFile, ThrowingRunnable action) throws Exception {
        Files.writeString(
                Path.of(pidFile),
                Long.toString(ProcessHandle.current().pid()) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        action.run();
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static String jsonLine(String text) {
        return "[{\"text\":\"" + escapeJson(text) + "\"}]\n";
    }

    private static String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            switch (value) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (value < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) value));
                    } else {
                        escaped.append(value);
                    }
                }
            }
        }
        return escaped.toString();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
