package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.ulviar.icli.command.CommandExecutionException;
import com.github.ulviar.icli.session.PooledProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolTranscript;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MystemProtocolFailureMapperTest {
    @Test
    void doesNotLeakStdoutTranscriptIntoSessionExceptionMessageOrStderr() {
        ProtocolSessionException source = new ProtocolSessionException(
                ProtocolSessionException.Reason.PROCESS_EXITED,
                new ProtocolTranscript("stdout: [{\"text\":\"secret user text\"}]", false, false, false),
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
                        "stdout: [{\"text\":\"secret\"}]\nstderr: bad mystem\nstderr: details", true, false, true),
                OptionalInt.of(7),
                "session failed",
                null);

        MystemProcessException mapped = (MystemProcessException) MystemProtocolFailureMapper.map(source);

        assertTrue(mapped.getMessage().contains("[truncated]"));
        assertTrue(mapped.getMessage().contains("[redacted]"));
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
        PooledProtocolSessionException source = new PooledProtocolSessionException(
                PooledProtocolSessionException.Reason.STARTUP_FAILED, "could not start pool");

        MystemException mapped = MystemProtocolFailureMapper.map(source);

        assertEquals(MystemStartupException.class, mapped.getClass());
    }
}
