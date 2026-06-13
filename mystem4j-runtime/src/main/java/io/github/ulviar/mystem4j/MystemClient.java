package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Raw MyStem client backed by one or more external MyStem CLI processes.
 *
 * <p>Runtime clients created by {@link Mystem#builder()} have different concurrency characteristics:
 * one-shot and pooled clients can serve concurrent text requests, while reusable-session clients serialize
 * text requests through one process. File requests are delegated to one-shot execution by all built-in modes.
 * Custom implementations should document their own thread-safety contract.
 */
public interface MystemClient extends AutoCloseable {
    /**
     * Returns the process and concurrency profile of this client.
     *
     * <p>Custom implementations may keep the default {@link MystemClientExecutionProfile#UNKNOWN}. Runtime clients
     * created by {@link Mystem#builder()} return a concrete profile so integrations can make explicit performance and
     * concurrency decisions.
     *
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
     * @return known output format, or an empty value when the client does not expose it
     */
    default Optional<MystemOutputFormat> outputFormat() {
        return Optional.empty();
    }

    /**
     * Analyzes one text request and returns raw MyStem output.
     *
     * @param text input text
     * @return raw result
     * @throws MystemException when the request cannot be executed or MyStem fails
     * @throws NullPointerException when {@code text} is {@code null}
     */
    MystemRawResult analyze(String text);

    /**
     * Analyzes an input file and captures stdout as a string.
     *
     * @param input readable input file
     * @return raw file content result
     * @throws MystemException when the request cannot be executed, file arguments are invalid, or MyStem fails
     * @throws NullPointerException when {@code input} is {@code null}
     */
    MystemFileContentResult analyzeFile(Path input);

    /**
     * Analyzes an input file and writes MyStem output directly to another file.
     *
     * @param input readable input file
     * @param output output file to create or overwrite
     * @return file result metadata
     * @throws MystemException when the request cannot be executed, file arguments are invalid, or MyStem fails
     * @throws NullPointerException when {@code input} or {@code output} is {@code null}
     */
    MystemFileResult analyzeFile(Path input, Path output);

    /**
     * Analyzes a collection of text requests sequentially.
     *
     * @param texts input texts
     * @return raw results in input order
     * @throws MystemException when any delegated request fails
     * @throws NullPointerException when {@code texts} or one of its elements is {@code null}
     */
    default List<MystemRawResult> analyzeAll(Collection<String> texts) {
        return texts.stream().map(this::analyze).toList();
    }

    /**
     * Closes all process resources owned by this client.
     */
    @Override
    void close();
}
