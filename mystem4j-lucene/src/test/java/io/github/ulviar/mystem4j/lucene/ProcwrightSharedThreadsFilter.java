package io.github.ulviar.mystem4j.lucene;

import com.carrotsearch.randomizedtesting.ThreadFilter;
import java.util.Arrays;

/** Excludes only idle JVM-wide executors owned by Procwright 0.1.0. */
public final class ProcwrightSharedThreadsFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread thread) {
        if (!thread.isDaemon()
                || (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING)) {
            return false;
        }
        String name = thread.getName();
        if (!name.matches("procwright-retirement-[0-7]") && !name.matches("procwright-pty-launch-[1-9][0-9]*")) {
            return false;
        }
        // These executors survive individual clients. Running cleanup, process I/O and
        // blocked callbacks remain subject to the normal Lucene leak checks.
        return Arrays.stream(thread.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals("java.util.concurrent.ThreadPoolExecutor")
                        && frame.getMethodName().equals("getTask"));
    }
}
