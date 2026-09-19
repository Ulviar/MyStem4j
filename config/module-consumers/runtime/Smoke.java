package consumer;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemExecutableNotFoundException;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import java.nio.file.Path;

public final class Smoke {
    public static void main(String[] args) {
        MystemOptions options = MystemOptions.builder().grammarInfo(true).build();
        if (options.format() != MystemOutputFormat.JSON) {
            throw new AssertionError("Unexpected runtime defaults");
        }
        try (var client = Mystem.builder().options(options)
                .executable(Path.of("missing-consumer-test-mystem")).searchPath(false).build()) {
            throw new AssertionError("Resolved missing executable: " + client);
        } catch (MystemExecutableNotFoundException expected) {
            // Exercise the builder without requiring a native executable.
        }
    }
}
