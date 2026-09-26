/**
 * Runs an installed Yandex MyStem executable and returns raw text, JSON, or XML output.
 *
 * <p>Start with {@link io.github.ulviar.mystem4j.Mystem#builder() Mystem.builder()}; the
 * {@link io.github.ulviar.mystem4j.Mystem} class contains a complete usage example. Configure an executable
 * explicitly, or use the {@code mystem4j.executable} system property, {@code MYSTEM_PATH}, or PATH, in that
 * order. The runtime does not download MyStem or accept its license on the caller's behalf.
 *
 * <h2>Choose a process mode</h2>
 * <table class="striped">
 * <caption>Built-in client modes</caption>
 * <thead><tr><th scope="col">Mode</th><th scope="col">Use</th><th scope="col">Text requests</th></tr></thead>
 * <tbody>
 * <tr><th scope="row">One-shot (default)</th><td>Occasional requests or multiline input</td>
 * <td>One process per call; JSON, XML, or text</td></tr>
 * <tr><th scope="row">{@link io.github.ulviar.mystem4j.MystemClientBuilder#session() Session}</th>
 * <td>Repeated calls through one process</td><td>Serialized; single-line JSON only</td></tr>
 * <tr><th scope="row">{@link io.github.ulviar.mystem4j.MystemClientBuilder#pooled() Pool}</th>
 * <td>Concurrent analysis with bounded worker capacity</td><td>Concurrent; single-line JSON only</td></tr>
 * </tbody>
 * </table>
 *
 * <p>Every mode executes file requests in separate one-shot processes. Always close the client, preferably
 * with try-with-resources. {@link io.github.ulviar.mystem4j.MystemClient} specifies thread safety, file
 * ownership, interruption, and shutdown behavior.
 *
 * <h2>Results and failures</h2>
 * <p>{@link io.github.ulviar.mystem4j.MystemRawResult} and the file result types contain raw output or file
 * metadata, not parsed morphology. Selecting JSON does not validate response syntax. Character limits and
 * {@link io.github.ulviar.mystem4j.MystemRequestStats statistics} count Java UTF-16 code units; they are not
 * token offsets. Morphology parsing and offset reconstruction belong to the separate model module.
 *
 * <p>Execution failures use {@link io.github.ulviar.mystem4j.MystemException} subclasses. Invalid request
 * payloads are rejected before execution. A failed reusable session must be closed and replaced; a pool
 * replaces failed workers. Neither path automatically retries a failed analysis.
 */
package io.github.ulviar.mystem4j;
