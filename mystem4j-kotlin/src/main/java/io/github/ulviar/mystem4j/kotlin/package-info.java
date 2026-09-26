/**
 * Kotlin configuration DSL and extension functions for the MyStem4j runtime.
 *
 * <p>The Kotlin entry points {@code mystemClient { ... }} and {@code mystemOptions { ... }} use the
 * same defaults and validation as {@link io.github.ulviar.mystem4j.MystemClientBuilder} and
 * {@link io.github.ulviar.mystem4j.MystemOptions.Builder}. Configuration receivers are mutable and
 * intended for use inside their blocks; resulting clients retain the Java runtime's lifecycle and concurrency
 * contracts. Use Kotlin's {@code use { ... }} to close a client after its final request.
 *
 * <p>The {@code analyzeWith} extensions on {@code String}, {@link java.nio.file.Path}, and
 * {@link java.io.File} are synchronous calls returning raw output. They do not close the supplied
 * client or parse morphology. Kotlin declarations and examples are documented in the module's Dokka
 * reference; Java runtime contracts are defined by {@link io.github.ulviar.mystem4j.MystemClient}.
 */
package io.github.ulviar.mystem4j.kotlin;
