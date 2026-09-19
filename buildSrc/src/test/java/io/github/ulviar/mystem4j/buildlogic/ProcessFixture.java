package io.github.ulviar.mystem4j.buildlogic;

import java.nio.file.Files;
import java.nio.file.Path;

final class ProcessFixture {
    private ProcessFixture() {}

    public static void main(String[] arguments) throws Exception {
        Files.writeString(Path.of(arguments[1]), Long.toString(ProcessHandle.current().pid()));
        switch (arguments[0]) {
            case "stderr" -> {
                System.err.print("e".repeat(512 * 1024));
                System.out.print("ok");
            }
            case "sleep" -> Thread.sleep(60_000);
            case "overflow" -> System.out.print("x".repeat(5 * 1024 * 1024));
            case "failure" -> {
                System.err.print("expected diagnostic");
                System.exit(7);
            }
            default -> throw new IllegalArgumentException(arguments[0]);
        }
    }
}
