package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ulviar.procwright.command.CommandExecutionException;
import io.github.ulviar.procwright.session.PooledSessionException;
import io.github.ulviar.procwright.session.ProtocolSessionException;
import io.github.ulviar.procwright.session.ProtocolTranscript;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MystemProtocolFailureMapperTest {
    @Test
    void mapsProcessCleanupFailureToProtocolException() {
        CommandExecutionException source = new CommandExecutionException(
                CommandExecutionException.Reason.RUNTIME_FAILURE, "cleanup failed");

        MystemException mapped = MystemProtocolFailureMapper.map(source, "Failed to close reusable MyStem session");

        assertEquals(MystemProtocolException.class, mapped.getClass());
        assertSame(source, mapped.getCause());
        assertTrue(mapped.getMessage().contains("Failed to close reusable MyStem session"));
    }

    @Test
    void preservesNewPoolLifecycleFailuresBehindMystemExceptionContract() {
        for (PooledSessionException.Reason reason : java.util.List.of(
                PooledSessionException.Reason.INTERRUPTED, PooledSessionException.Reason.DRAIN_TIMEOUT)) {
            PooledSessionException source = new PooledSessionException(reason, "pool lifecycle failed");
            MystemException mapped = MystemProtocolFailureMapper.map(source);

            assertEquals(MystemProtocolException.class, mapped.getClass());
            assertSame(source, mapped.getCause());
        }
    }

    @Test
    void doesNotLeakStdoutTranscriptIntoSessionExceptionMessageOrStderr() {
        ProtocolSessionException source = new ProtocolSessionException(
                ProtocolSessionException.Reason.PROCESS_EXITED,
                new ProtocolTranscript("stdout: [{\"text\":\"secret user text\"}]", false, false),
                OptionalInt.of(7),
                "session failed",
                null);

        MystemProcessException mapped = (MystemProcessException) MystemProtocolFailureMapper.map(source);

        assertEquals(7, mapped.exitCode().orElseThrow());
        assertFalse(mapped.getMessage().contains("secret user text"));
        assertFalse(mapped.stderr().contains("secret user text"));
    }

    @Test
    void preservesStderrLinesAndTranscriptFlags() {
        ProtocolSessionException source = new ProtocolSessionException(
                ProtocolSessionException.Reason.PROCESS_EXITED,
                new ProtocolTranscript(
                        "stdout: [{\"text\":\"secret\"}]\nstderr: bad mystem\nstderr: details", true, true),
                OptionalInt.of(7),
                "session failed",
                null);

        MystemProcessException mapped = (MystemProcessException) MystemProtocolFailureMapper.map(source);

        assertTrue(mapped.getMessage().contains("[truncated]"));
        assertTrue(mapped.getMessage().contains("[malformed]"));
        assertTrue(mapped.getMessage().contains("bad mystem"));
        assertFalse(mapped.getMessage().contains("\"secret\""));
        assertEquals("bad mystem\ndetails", mapped.stderr());
    }

    @Test
    void mapsCommandLaunchFailureToStartupException() {
        CommandExecutionException source = new CommandExecutionException(
                CommandExecutionException.Reason.LAUNCH_FAILED, "could not start");

        MystemException mapped = MystemProtocolFailureMapper.map(source, "Failed to execute MyStem process");

        assertEquals(MystemStartupException.class, mapped.getClass());
    }

    @Test
    void mapsPoolStartupFailureToStartupException() {
        PooledSessionException source = new PooledSessionException(
                PooledSessionException.Reason.STARTUP_FAILED, "could not start pool");

        MystemException mapped = MystemProtocolFailureMapper.map(source);

        assertEquals(MystemStartupException.class, mapped.getClass());
    }
}
