package io.github.ulviar.mystem4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MystemProbeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void probesExecutableWithSmokeRequest() throws IOException {
        Path executable = fakeMystem();

        MystemProbeResult result = MystemProbe.probe(executable);

        assertEquals(executable, result.executable());
        assertEquals(MystemOutputFormat.JSON, result.format());
        assertEquals("[{\"text\":\"мама\"}]\n", result.output());
        assertTrue(result.elapsed().toNanos() >= 0);
    }

    @Test
    void rejectsNonMystemProbeOutput() throws IOException {
        Path executable = FakeMystemExecutable.create(temporaryDirectory, "not-mystem", "notMystem");

        assertThrows(MystemProtocolException.class, () -> MystemProbe.probe(executable));
    }

    private Path fakeMystem() throws IOException {
        return FakeMystemExecutable.create(temporaryDirectory, "fake-mystem", "echo");
    }
}
