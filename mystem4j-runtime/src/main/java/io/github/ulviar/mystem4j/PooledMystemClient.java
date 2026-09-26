package io.github.ulviar.mystem4j;

import io.github.ulviar.procwright.session.PooledProtocolSession;
import io.github.ulviar.procwright.session.PooledSessionException;
import io.github.ulviar.procwright.session.ProtocolSessionException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class PooledMystemClient implements MystemClient {
    private final PooledProtocolSession<String, String> pool;
    private final OneShotMystemClient fileClient;
    private final MystemOptions options;
    private final Duration requestTimeout;
    private final MystemRequestLimits requestLimits;
    private final Semaphore requestSlots;
    private final long acquireTimeoutNanos;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantReadWriteLock closeLock = new ReentrantReadWriteLock();

    PooledMystemClient(
            PooledProtocolSession<String, String> pool,
            OneShotMystemClient fileClient,
            MystemOptions options,
            Duration requestTimeout,
            MystemRequestLimits requestLimits,
            MystemPoolOptions poolOptions) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.fileClient = Objects.requireNonNull(fileClient, "fileClient");
        this.options = Objects.requireNonNull(options, "options");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.requestLimits = requestLimits;
        // Keep FIFO admission independent of Procwright's worker acquisition policy, so a hot caller
        // cannot repeatedly take a worker ahead of callers already waiting for one.
        this.requestSlots = new Semaphore(poolOptions.maxSize(), true);
        this.acquireTimeoutNanos = TimeUnit.NANOSECONDS.convert(poolOptions.acquireTimeout());
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
            int inputBytes = requestLimits.validate(text);
            long started = System.nanoTime();
            acquireRequestSlot();
            try {
                String output = pool.request(text, requestTimeout);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
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
            } catch (PooledSessionException error) {
                throw MystemProtocolFailureMapper.map(error);
            } finally {
                requestSlots.release();
            }
        } finally {
            closeLock.readLock().unlock();
        }
    }

    private void acquireRequestSlot() {
        try {
            if (!requestSlots.tryAcquire(acquireTimeoutNanos, TimeUnit.NANOSECONDS)) {
                throw new MystemPoolExhaustedException("Timed out waiting for a MyStem pool request slot.", null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new MystemProtocolException("Interrupted while waiting for a MyStem pool request slot.", error);
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
                try {
                    pool.close();
                } catch (PooledSessionException error) {
                    throw MystemProtocolFailureMapper.map(error);
                } finally {
                    fileClient.close();
                }
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
