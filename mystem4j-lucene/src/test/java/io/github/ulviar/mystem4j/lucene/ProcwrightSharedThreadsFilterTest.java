package io.github.ulviar.mystem4j.lucene;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class ProcwrightSharedThreadsFilterTest {
    @Test
    public void excludesIdleSharedExecutorButKeepsBlockedWorkAndSessionThreadsVisible() throws Exception {
        ProcwrightSharedThreadsFilter filter = new ProcwrightSharedThreadsFilter();
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = Thread.ofPlatform().daemon(true).name("procwright-pty-launch-1").unstarted(task);
            worker.set(thread);
            return thread;
        })) {
            try {
                executor.submit(() -> {
                    started.countDown();
                    release.await();
                    return null;
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertFalse("Blocked task must remain visible", filter.reject(worker.get()));
            } finally {
                release.countDown();
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!filter.reject(worker.get()) && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue("Idle shared executor may survive client close", filter.reject(worker.get()));
            worker.get().setName("procwright-protocol-stdout-0");
            assertFalse("Process stream threads must remain visible", filter.reject(worker.get()));
        }
    }
}
