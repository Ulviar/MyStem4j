package io.github.ulviar.mystem4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FakeMystemExecutable {
    private FakeMystemExecutable() {}

    static Path create(Path directory, String name, String mode, String... modeArgs) throws IOException {
        Path executable = directory.resolve(name + executableSuffix());
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaExecutableName()).toString();
        String classpath = System.getProperty("java.class.path");
        if (isWindows()) {
            Files.writeString(
                    executable,
                    windowsLauncher(javaExecutable, classpath, mode, modeArgs),
                    StandardCharsets.UTF_8);
        } else {
            Files.writeString(
                    executable,
                    unixLauncher(javaExecutable, classpath, mode, modeArgs),
                    StandardCharsets.UTF_8);
            executable.toFile().setExecutable(true, false);
        }
        return executable;
    }

    static Path brokenExecutable(Path directory, String name) throws IOException {
        Path executable = directory.resolve(name + (isWindows() ? ".exe" : ""));
        Files.writeString(executable, "not a real executable", StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true, false);
        return executable;
    }

    private static String unixLauncher(String javaExecutable, String classpath, String mode, String[] modeArgs) {
        StringBuilder launcher = new StringBuilder("#!/bin/sh\nexec ")
                .append(shellQuote(javaExecutable))
                .append(" -cp ")
                .append(shellQuote(classpath))
                .append(' ')
                .append(FakeMystemProcess.class.getName())
                .append(' ')
                .append(shellQuote(mode));
        for (String arg : modeArgs) {
            launcher.append(' ').append(shellQuote(arg));
        }
        return launcher.append(" \"$@\"\n").toString();
    }

    private static String windowsLauncher(String javaExecutable, String classpath, String mode, String[] modeArgs) {
        StringBuilder launcher = new StringBuilder("@echo off\r\n\"")
                .append(javaExecutable)
                .append("\" -cp \"")
                .append(classpath)
                .append("\" ")
                .append(FakeMystemProcess.class.getName())
                .append(" \"")
                .append(mode)
                .append('"');
        for (String arg : modeArgs) {
            launcher.append(" \"").append(arg).append('"');
        }
        return launcher.append(" %*\r\nexit /b %ERRORLEVEL%\r\n").toString();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String executableSuffix() {
        return isWindows() ? ".cmd" : "";
    }

    private static String javaExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
