package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemRuntimeResourceReleaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reusableSessionCloseStopsWorkerProcess() throws Exception {
        Path pidFile = temporaryDirectory.resolve("session-pids.txt");
        Path executable = pidRecordingInteractiveMystem(pidFile);
        MystemClient client = Mystem.builder().executable(executable).session().build();

        client.analyze("one");
        long pid = waitForPidCount(pidFile, 1).getFirst();

        client.close();

        waitUntilProcessExits(pid);
        assertThrows(MystemClosedException.class, () -> client.analyze("two"));
    }

    @Test
    void pooledClientCloseStopsWarmWorkers() throws Exception {
        Path pidFile = temporaryDirectory.resolve("pool-pids.txt");
        Path executable = pidRecordingInteractiveMystem(pidFile);
        MystemClient client = Mystem.builder()
                .executable(executable)
                .pooled(pool -> pool.maxSize(2)
                        .warmupSize(2)
                        .minIdle(2)
                        .acquireTimeout(Duration.ofSeconds(1)))
                .build();

        List<Long> pids = waitForPidCount(pidFile, 2);

        client.close();

        for (long pid : pids) {
            waitUntilProcessExits(pid);
        }
        assertThrows(MystemClosedException.class, () -> client.analyze("one"));
    }

    @Test
    void oneShotTimeoutStopsWorkerProcess() throws Exception {
        Path pidFile = temporaryDirectory.resolve("timeout-pids.txt");
        Path executable = pidRecordingSleepingMystem(pidFile);
        try (MystemClient client = Mystem.builder()
                .executable(executable)
                .requestTimeout(Duration.ofSeconds(1))
                .build()) {
            assertThrows(MystemRequestTimeoutException.class, () -> client.analyze("text"));
        }

        long pid = waitForPidCount(pidFile, 1).getFirst();

        waitUntilProcessExits(pid);
    }

    private Path pidRecordingInteractiveMystem(Path pidFile) throws IOException {
        return FakeMystemExecutable.create(
                temporaryDirectory, "pid-recording-interactive-mystem", "recordPidInteractive", pidFile.toString());
    }

    private Path pidRecordingSleepingMystem(Path pidFile) throws IOException {
        return FakeMystemExecutable.create(
                temporaryDirectory, "pid-recording-sleeping-mystem", "recordPidSleep", pidFile.toString());
    }

    private static List<Long> waitForPidCount(Path pidFile, int expected) throws InterruptedException, IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        List<Long> pids = List.of();
        while (System.nanoTime() < deadline) {
            pids = readPids(pidFile);
            if (pids.size() >= expected) {
                return pids;
            }
            Thread.sleep(25);
        }
        assertTrue(pids.size() >= expected, "expected at least " + expected + " worker pids in " + pidFile);
        return pids;
    }

    private static List<Long> readPids(Path pidFile) throws IOException {
        if (!Files.isRegularFile(pidFile)) {
            return List.of();
        }
        LinkedHashSet<Long> pids = new LinkedHashSet<>();
        for (String line : Files.readAllLines(pidFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                pids.add(Long.parseLong(line.strip()));
            }
        }
        return List.copyOf(pids);
    }

    private static void waitUntilProcessExits(long pid) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline && isProcessAlive(pid)) {
            Thread.sleep(25);
        }
        boolean alive = isProcessAlive(pid);
        if (alive) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        }
        assertFalse(alive, "worker process should exit after client close or timeout: pid=" + pid);
    }

    private static boolean isProcessAlive(long pid) {
        Optional<ProcessHandle> process = ProcessHandle.of(pid);
        return process.isPresent() && process.orElseThrow().isAlive();
    }
}
