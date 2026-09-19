package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessSupportTest {
    @TempDir
    Path temporary;

    @Test
    void drainsStderrWhileReadingStdout() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        Path pid = temporary.resolve("pid");
        try {
            var result = executor.submit(() -> ProcessSupport.run(command("stderr", pid), temporary.toFile()));
            assertEquals("ok", result.get(5, TimeUnit.SECONDS));
        } finally {
            killRecordedProcess(pid);
            executor.shutdownNow();
        }
    }

    @Test
    void reportsExitCodeAndStderr() {
        var failure = assertThrows(GradleException.class,
                () -> ProcessSupport.run(command("failure", temporary.resolve("pid")), temporary.toFile()));
        assertTrue(failure.getMessage().contains("exit 7"));
        assertTrue(failure.getMessage().contains("expected diagnostic"));
    }

    @Test
    void rejectsTruncatedOutput() {
        var failure = assertThrows(GradleException.class,
                () -> ProcessSupport.run(command("overflow", temporary.resolve("pid")), temporary.toFile()));
        assertTrue(failure.getMessage().contains("output exceeded"));
    }

    @Test
    void timeoutTerminatesChild() throws Exception {
        Path pid = temporary.resolve("pid");
        try {
            var failure = assertThrows(GradleException.class,
                    () -> ProcessSupport.run(command("sleep", pid), temporary.toFile(), Duration.ofSeconds(2)));
            assertTrue(failure.getMessage().contains("timed out"));
            assertExited(pid);
        } finally {
            killRecordedProcess(pid);
        }
    }

    @Test
    void interruptionTerminatesChildAndPreservesFlag() throws Exception {
        Path pid = temporary.resolve("pid");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                ProcessSupport.run(command("sleep", pid), temporary.toFile());
            } catch (Throwable error) {
                failure.set(error);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Files.exists(pid) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(Files.exists(pid), "child must start before interruption");
            thread.interrupt();
            thread.join(5000);
            assertFalse(thread.isAlive());
            assertTrue(failure.get() instanceof GradleException);
            assertTrue(interrupted.get());
            assertExited(pid);
        } finally {
            thread.interrupt();
            killRecordedProcess(pid);
        }
    }

    private static void assertExited(Path pid) throws Exception {
        ProcessHandle process = ProcessHandle.of(Long.parseLong(Files.readString(pid))).orElse(null);
        if (process != null) {
            process.onExit().get(5, TimeUnit.SECONDS);
            assertFalse(process.isAlive());
        }
    }

    private static List<String> command(String mode, Path pid) {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        try {
            String classes = Path.of(ProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
            return List.of(java, "-cp", classes, ProcessFixture.class.getName(), mode, pid.toString());
        } catch (java.net.URISyntaxException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void killRecordedProcess(Path pid) throws Exception {
        if (Files.exists(pid)) {
            ProcessHandle.of(Long.parseLong(Files.readString(pid))).ifPresent(ProcessHandle::destroyForcibly);
        }
    }
}
