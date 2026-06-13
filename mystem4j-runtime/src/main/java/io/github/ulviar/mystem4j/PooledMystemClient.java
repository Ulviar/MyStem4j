package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.session.PooledProtocolSession;
import com.github.ulviar.icli.session.PooledProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolSessionException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class PooledMystemClient implements MystemClient {
    private final PooledProtocolSession<String, String> pool;
    private final OneShotMystemClient fileClient;
    private final MystemOptions options;
    private final Duration requestTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock closeLock = new ReentrantReadWriteLock();

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
    public MystemClientExecutionProfile executionProfile() {
        return MystemClientExecutionProfile.POOLED_SESSIONS;
    }

    @Override
    public Optional<MystemOutputFormat> outputFormat() {
        return Optional.of(options.format());
    }

    @Override
    public MystemRawResult analyze(String text) {
        closeLock.readLock().lock();
        try {
            ensureOpen();
            Objects.requireNonNull(text, "text");
            MystemJsonLineProtocol.validateRequest(text);
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
                        output.getBytes(options.encoding().charset()).length);
                return new MystemRawResult(text, output, options.format(), stats);
            } catch (ProtocolSessionException error) {
                throw MystemProtocolFailureMapper.map(error);
            } catch (PooledProtocolSessionException error) {
                throw MystemProtocolFailureMapper.map(error);
            }
        } finally {
            closeLock.readLock().unlock();
        }
    }

    @Override
    public MystemFileContentResult analyzeFile(Path input) {
        closeLock.readLock().lock();
        try {
            ensureOpen();
            return fileClient.analyzeFile(input);
        } finally {
            closeLock.readLock().unlock();
        }
    }

    @Override
    public MystemFileResult analyzeFile(Path input, Path output) {
        closeLock.readLock().lock();
        try {
            ensureOpen();
            return fileClient.analyzeFile(input, output);
        } finally {
            closeLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        closeLock.writeLock().lock();
        try {
            if (closed.compareAndSet(false, true)) {
                pool.close();
                fileClient.close();
            }
        } finally {
            closeLock.writeLock().unlock();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new MystemClosedException("MyStem client is closed.");
        }
    }

}
