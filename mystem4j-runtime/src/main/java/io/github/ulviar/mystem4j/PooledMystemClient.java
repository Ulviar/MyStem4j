package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.session.PooledProtocolSession;
import com.github.ulviar.icli.session.PooledProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolSessionException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

final class PooledMystemClient implements MystemClient {
    private final PooledProtocolSession<String, String> pool;
    private final OneShotMystemClient fileClient;
    private final MystemOptions options;
    private final Duration requestTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();

    PooledMystemClient(
            PooledProtocolSession<String, String> pool,
            OneShotMystemClient fileClient,
            MystemOptions options,
            Duration requestTimeout) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.fileClient = Objects.requireNonNull(fileClient, "fileClient");
        this.options = Objects.requireNonNull(options, "options");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public MystemRawResult analyze(String text) {
        ensureOpen();
        Objects.requireNonNull(text, "text");
        validateJsonLineInput(text);
        long started = System.nanoTime();
        try {
            String output = pool.request(text, requestTimeout);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            int inputBytes = text.getBytes(options.encoding().charset()).length;
            MystemRequestStats stats = new MystemRequestStats(
                    elapsed,
                    MystemExecutionMode.POOL,
                    text.length(),
                    inputBytes,
                    output.length(),
                    output.getBytes(options.encoding().charset()).length,
                    OptionalInt.empty(),
                    false);
            return new MystemRawResult(text, output, options.format(), stats);
        } catch (ProtocolSessionException error) {
            throw MystemProtocolFailureMapper.map(error);
        } catch (PooledProtocolSessionException error) {
            throw MystemProtocolFailureMapper.map(error);
        }
    }

    @Override
    public MystemFileContentResult analyzeFile(Path input) {
        ensureOpen();
        return fileClient.analyzeFile(input);
    }

    @Override
    public MystemFileResult analyzeFile(Path input, Path output) {
        ensureOpen();
        return fileClient.analyzeFile(input, output);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pool.close();
            fileClient.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new MystemClosedException("MyStem client is closed.");
        }
    }

    private static void validateJsonLineInput(String text) {
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new MystemInvalidOptionsException("Reusable MyStem JSON line protocol rejects multiline input.");
        }
    }
}
