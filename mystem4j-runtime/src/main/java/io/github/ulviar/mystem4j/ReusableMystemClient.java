package io.github.ulviar.mystem4j;

import io.github.ulviar.procwright.command.CommandExecutionException;
import io.github.ulviar.procwright.session.ProtocolSession;
import io.github.ulviar.procwright.session.ProtocolSessionException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ReusableMystemClient implements MystemClient {
    private final ProtocolSession<String, String> session;
    private final OneShotMystemClient fileClient;
    private final MystemOptions options;
    private final Duration requestTimeout;
    private final MystemRequestLimits requestLimits;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<MystemException> terminalFailure = new AtomicReference<>();

    ReusableMystemClient(
            ProtocolSession<String, String> session,
            OneShotMystemClient fileClient,
            MystemOptions options,
            Duration requestTimeout,
            MystemRequestLimits requestLimits) {
        this.session = Objects.requireNonNull(session, "session");
        this.fileClient = Objects.requireNonNull(fileClient, "fileClient");
        this.options = Objects.requireNonNull(options, "options");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.requestLimits = requestLimits;
    }

    @Override
    public MystemClientExecutionProfile executionProfile() {
        return MystemClientExecutionProfile.REUSABLE_SESSION;
    }

    @Override
    public Optional<MystemOutputFormat> outputFormat() {
        return Optional.of(options.format());
    }

    @Override
    public synchronized MystemRawResult analyze(String text) {
        ensureOpen();
        Objects.requireNonNull(text, "text");
        MystemJsonLineProtocol.validateRequest(text);
        int inputBytes = requestLimits.validate(text);
        long started = System.nanoTime();
        try {
            String output = session.request(text, requestTimeout);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            MystemRequestStats stats = new MystemRequestStats(
                    elapsed,
                    MystemExecutionMode.SESSION,
                    text.length(),
                    inputBytes,
                    output.length(),
                    output.getBytes(options.encoding().charset()).length);
            return new MystemRawResult(text, output, options.format(), stats);
        } catch (ProtocolSessionException error) {
            MystemException mapped = MystemProtocolFailureMapper.map(error);
            if (isTerminalSessionFailure(error)) {
                terminalFailure.compareAndSet(null, mapped);
            }
            throw mapped;
        }
    }

    @Override
    public synchronized MystemFileContentResult analyzeFile(Path input) {
        ensureOpen();
        return fileClient.analyzeFile(input);
    }

    @Override
    public synchronized MystemFileResult analyzeFile(Path input, Path output) {
        ensureOpen();
        return fileClient.analyzeFile(input, output);
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                session.close();
            } catch (CommandExecutionException error) {
                throw MystemProtocolFailureMapper.map(error, "Failed to close reusable MyStem session");
            } finally {
                fileClient.close();
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new MystemClosedException("MyStem client is closed.");
        }
        MystemException failure = terminalFailure.get();
        if (failure != null) {
            throw new MystemProtocolException("Reusable MyStem session is not usable after a previous failure.", failure);
        }
    }

    private static boolean isTerminalSessionFailure(ProtocolSessionException error) {
        return switch (error.reason()) {
            case TIMEOUT, CLOSED, EOF, BROKEN_PIPE, DECODE_ERROR, RESPONSE_TOO_LARGE,
                            PROTOCOL_DECODER_FAILED, PROCESS_EXITED, FAILURE ->
                    true;
            case REQUEST_TOO_LARGE -> false;
        };
    }
}
