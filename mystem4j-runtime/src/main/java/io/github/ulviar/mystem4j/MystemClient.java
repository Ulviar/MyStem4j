package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Raw MyStem client backed by one or more external MyStem CLI processes.
 *
 * <p>Clients created by {@link Mystem#builder()} may be shared between threads. One-shot and pooled clients
 * can serve concurrent requests; reusable-session clients serialize all request calls, including file
 * requests. File requests use separate one-shot processes in every mode and do not consume pool capacity.
 * Custom implementations should document their own thread-safety and lifecycle contracts.
 *
 * <p>A successful request returns raw output without morphology parsing or JSON/XML validation. The caller
 * owns the client and must {@link #close() close} it; results remain usable after closing. See
 * {@link Mystem} for a try-with-resources example and {@link MystemClientBuilder} for limits and timeouts.
 */
public interface MystemClient extends AutoCloseable {
    /**
     * Returns the process and concurrency profile of this client.
     *
     * <p>Custom implementations may keep the default {@link MystemClientExecutionProfile#UNKNOWN}. Runtime clients
     * created by {@link Mystem#builder()} return a concrete profile so integrations can make explicit performance and
     * concurrency decisions.
     *
     * @implSpec The default implementation returns {@link MystemClientExecutionProfile#UNKNOWN}.
     * @return client execution profile
     */
    default MystemClientExecutionProfile executionProfile() {
        return MystemClientExecutionProfile.UNKNOWN;
    }

    /**
     * Returns the configured MyStem output format when the implementation can expose it without executing a request.
     *
     * <p>Custom implementations may keep the default empty value. Runtime clients created by {@link Mystem#builder()}
     * return the format configured in {@link MystemOptions}.
     *
     * @implSpec The default implementation returns {@link Optional#empty()} without executing a request.
     * @return known output format, or an empty value when the client does not expose it
     */
    default Optional<MystemOutputFormat> outputFormat() {
        return Optional.empty();
    }

    /**
     * Analyzes one text request and returns raw MyStem output.
     *
     * <p>Built-in session and pool clients accept single-line text without CR or LF only. One-shot clients
     * accept multiline text. Payload limits count UTF-16 code units and bytes in the configured encoding,
     * excluding any protocol newline. Invalid input rejected before execution leaves the client usable.
     *
     * <p>Execution failures can make a reusable session unusable; close and replace it. Pools replace failed
     * workers without retrying the failed request. Interrupted requests preserve the caller's interrupt flag
     * and fail with {@link MystemException}. A pool caller interrupted while waiting for admission does not
     * terminate another caller's worker.
     *
     * @param text non-null input text, including an empty string if desired
     * @return raw output with the original input and successful-request statistics
     * @throws MystemInvalidOptionsException when a payload limit is exceeded, or session/pool input contains CR or LF
     * @throws MystemClosedException when the client or its underlying session is closed
     * @throws MystemRequestTimeoutException when execution exceeds the configured request timeout
     * @throws MystemPoolExhaustedException when pool admission or worker acquisition times out
     * @throws MystemOutputLimitException when stdout exceeds a response limit, or one-shot stderr capture
     *     exceeds its byte limit; session/pool text requests discard excess stderr without failing
     * @throws MystemException when process startup, execution, or communication fails
     * @throws NullPointerException when {@code text} is {@code null}
     */
    MystemRawResult analyze(String text);

    /**
     * Analyzes an input file and captures stdout as a string.
     *
     * <p>Built-in clients always use a separate one-shot process. Input files are not subject to text payload
     * size limits or single-line restrictions; captured stdout is subject to response limits. Neither the
     * client nor the result owns or deletes the input file.
     *
     * @param input readable regular input file in the configured encoding
     * @return raw file content result
     * @throws MystemInvalidOptionsException when {@code input} is not a readable regular file
     * @throws MystemClosedException when the client is closed
     * @throws MystemOutputLimitException when captured stdout or stderr exceeds a response limit
     * @throws MystemException when process startup, execution, or communication fails
     * @throws NullPointerException when {@code input} is {@code null}
     */
    MystemFileContentResult analyzeFile(Path input);

    /**
     * Analyzes an input file and writes MyStem output directly to another file.
     *
     * <p>Built-in clients always use a separate one-shot process. File contents are not subject to in-memory
     * text payload or captured-response limits. Input and output must identify different files, including
     * through symlinks/hard links. The output parent directory must already exist. The caller retains file
     * ownership; an execution failure may leave partial output. Any stdout or stderr emitted in addition to
     * the output file is still captured with the configured response limits.
     *
     * @param input readable regular input file in the configured encoding
     * @param output writable output file to create or overwrite
     * @return file result metadata
     * @throws MystemInvalidOptionsException when the input is unreadable, the output is unwritable, its parent
     *     directory does not exist, or both paths identify the same file
     * @throws MystemClosedException when the client is closed
     * @throws MystemException when process startup, execution, or communication fails
     * @throws NullPointerException when {@code input} or {@code output} is {@code null}
     */
    MystemFileResult analyzeFile(Path input, Path output);

    /**
     * Analyzes a collection of text requests sequentially, stopping at the first failure.
     *
     * <p>This method does not parallelize requests on a pool or return partial results after a failure.
     * Earlier requests may already have completed when a later request fails; the batch is not atomic.
     *
     * @implSpec The default implementation calls {@link #analyze(String)} through a sequential stream and
     *     returns its unmodifiable result list. It does not prevalidate the entire collection. An empty
     *     collection returns an empty list without contacting or checking the client.
     * @param texts non-null collection of non-null input texts, in iteration order
     * @return unmodifiable raw results in input order
     * @throws MystemException when any delegated request fails
     * @throws NullPointerException when {@code texts} or one of its elements is {@code null}
     */
    default List<MystemRawResult> analyzeAll(Collection<String> texts) {
        return texts.stream().map(this::analyze).toList();
    }

    /**
     * Closes all process resources owned by this client.
     *
     * <p>Built-in clients wait for active requests to finish or reach their timeout before releasing resources.
     * For pools this includes requests already waiting for admission or worker acquisition. Closing is
     * idempotent and does not cancel work immediately. Later request calls fail with
     * {@link MystemClosedException}; metadata methods remain available.
     *
     * @throws MystemProtocolException if session or pool cleanup fails, the pool drain wait times out,
     *     or the waiting thread is interrupted; cleanup may continue after the exception
     */
    @Override
    void close();
}
