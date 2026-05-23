package io.github.ulviar.mystem4j;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Raw MyStem client backed by one or more external MyStem CLI processes.
 */
public interface MystemClient extends AutoCloseable {
    /**
     * Analyzes one text request and returns raw MyStem output.
     *
     * @param text input text
     * @return raw result
     */
    MystemRawResult analyze(String text);

    /**
     * Analyzes an input file and captures stdout as a string.
     *
     * @param input readable input file
     * @return raw file content result
     */
    MystemFileContentResult analyzeFile(Path input);

    /**
     * Analyzes an input file and writes MyStem output directly to another file.
     *
     * @param input readable input file
     * @param output output file to create or overwrite
     * @return file result metadata
     */
    MystemFileResult analyzeFile(Path input, Path output);

    /**
     * Analyzes a collection of text requests sequentially.
     *
     * @param texts input texts
     * @return raw results in input order
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
