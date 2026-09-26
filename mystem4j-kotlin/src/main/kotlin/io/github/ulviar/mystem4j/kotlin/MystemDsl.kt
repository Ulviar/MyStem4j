package io.github.ulviar.mystem4j.kotlin

import io.github.ulviar.mystem4j.Mystem
import io.github.ulviar.mystem4j.MystemClient
import io.github.ulviar.mystem4j.MystemClientBuilder
import io.github.ulviar.mystem4j.MystemEncoding
import io.github.ulviar.mystem4j.MystemFileContentResult
import io.github.ulviar.mystem4j.MystemOptions
import io.github.ulviar.mystem4j.MystemOutputFormat
import io.github.ulviar.mystem4j.MystemPoolOptions
import io.github.ulviar.mystem4j.MystemRawResult
import java.io.File
import java.nio.file.Path
import java.time.Duration
import kotlin.jvm.JvmName
import kotlin.time.Duration as KotlinDuration
import kotlin.time.toJavaDuration

/**
 * Restricts implicit calls in nested DSL blocks to the nearest MyStem4j receiver.
 *
 * Inside `options { ... }` or `pooled { ... }`, client-level configuration must use an
 * explicit outer receiver. This prevents an unqualified call from silently changing the client.
 */
@DslMarker
public annotation class MystemDslMarker

/**
 * Builds a MyStem runtime client with Kotlin receiver-style configuration.
 *
 * The DSL delegates to the Java runtime builder and keeps the same validation
 * rules, exceptions, and defaults: one-shot JSON/UTF-8, a three-second request timeout,
 * disabled idle timeout, enabled PATH lookup, and no full-input diagnostics. Close the
 * returned client with `use { ... }` when possible. Session and pool clients require
 * JSON, reject `newLineEachWord(true)`, and reject CR/LF in text requests.
 *
 * The callback runs synchronously on the calling thread with a fresh, mutable receiver.
 * After it returns, [MystemClientBuilder.build] validates configuration and resolves the
 * executable. Session mode also starts its process; pool mode performs configured warmup.
 * One-shot mode starts no process until a request. No executable is downloaded.
 *
 * ```kotlin
 * import io.github.ulviar.mystem4j.kotlin.analyzeWith
 * import io.github.ulviar.mystem4j.kotlin.mystemClient
 * import kotlin.time.Duration.Companion.seconds
 *
 * fun main(args: Array<String>) {
 *     require(args.size == 1) { "Pass the path to an installed MyStem executable" }
 *     mystemClient {
 *         executable(args[0])
 *         options {
 *             grammarInfo()
 *             disambiguate()
 *         }
 *         requestTimeout(5.seconds)
 *     }.use { client ->
 *         println("Мама мыла раму.".analyzeWith(client).output())
 *     }
 * }
 * ```
 *
 * Results contain raw output, not parsed morphology, and remain usable after the client closes.
 * Callback exceptions and Java runtime exceptions propagate without wrapping.
 *
 * @param configure configuration invoked exactly once before building the client
 * @return an independent client owned by the caller; close it even when a request fails
 * @throws io.github.ulviar.mystem4j.MystemInvalidOptionsException if CLI options are incompatible,
 * the selected process mode rejects them, or a fixlist is not a readable regular file
 * @throws io.github.ulviar.mystem4j.MystemExecutableNotFoundException if no usable executable is resolved
 * @throws io.github.ulviar.mystem4j.MystemException if session or pool startup fails
 * @throws IllegalArgumentException if a timeout, size limit, or pool setting is outside its allowed range
 * @see MystemClientBuilder
 * @see MystemClient
 */
public fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient {
    val builder = Mystem.builder()
    MystemClientDsl(builder).configure()
    return builder.build()
}

/**
 * Builds immutable MyStem CLI options with Kotlin receiver-style configuration.
 *
 * Defaults are JSON, UTF-8, and all flags disabled. Calling a boolean option without
 * an argument enables it; omitting the call preserves the Java default.
 *
 * The callback runs immediately with a fresh receiver; options are validated after it returns.
 * No executable is resolved or started, and fixlist files are not accessed at this stage.
 * The returned options can be reused across clients and threads.
 *
 * ```kotlin
 * import io.github.ulviar.mystem4j.kotlin.mystemOptions
 *
 * fun main() {
 *     val options = mystemOptions {
 *         grammarInfo()
 *         mergeWordForms() // Requires grammarInfo(), checked after the block.
 *     }
 *     check(options.grammarInfo() && options.mergeWordForms())
 * }
 * ```
 *
 * @param configure configuration invoked exactly once before option validation
 * @return immutable options snapshot, independent of subsequent receiver changes
 * @throws io.github.ulviar.mystem4j.MystemInvalidOptionsException if word-form merging lacks
 * grammar information, sentence markers lack input copying, or a grammar filter is blank
 * @see MystemOptions.Builder
 */
public fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions {
    val builder = MystemOptions.builder()
    MystemOptionsDsl(builder).configure()
    return builder.build()
}

/**
 * Mutable client configuration receiver supplied to [mystemClient].
 *
 * Configure it within the callback; it is not intended for concurrent use. Settings delegate
 * to [MystemClientBuilder], and later receiver changes do not reconfigure a built client.
 * Nested [options] blocks start from fresh CLI defaults; nested [pooled] blocks start from
 * fresh pool defaults. Repeating either block replaces the earlier configuration.
 *
 * Java and Kotlin duration overloads use the same runtime validation. Kotlin values are
 * converted with [toJavaDuration]; they are not rounded to whole seconds by this DSL.
 */
@MystemDslMarker
public class MystemClientDsl internal constructor(
    private val builder: MystemClientBuilder,
) {
    /**
     * Selects a local executable, taking precedence over property, environment, and PATH lookup.
     * The path is checked when [mystemClient] builds the client; relative paths are allowed.
     *
     * @param path path to a regular executable file
     * @see MystemClientBuilder.executable
     */
    public fun executable(path: Path): Unit {
        builder.executable(path)
    }

    /**
     * Converts [path] with [Path.of] and selects it as the executable.
     *
     * @throws java.nio.file.InvalidPathException if the string is not a valid path on this platform
     * @see executable
     */
    public fun executable(path: String): Unit {
        executable(Path.of(path))
    }

    /** Sets the MyStem executable path from a [File]. */
    public fun executable(file: File): Unit {
        executable(file.toPath())
    }

    /** Replaces the complete CLI configuration with the supplied immutable [options]. */
    public fun options(options: MystemOptions): Unit {
        builder.options(options)
    }

    /**
     * Builds CLI options from fresh defaults and replaces the previous options.
     * [configure] runs immediately; option dependencies are checked when the block returns.
     *
     * @see mystemOptions
     */
    public fun options(configure: MystemOptionsDsl.() -> Unit): Unit {
        options(mystemOptions(configure))
    }

    /** Enables PATH lookup by default; property and environment resolution remain active when disabled. */
    public fun searchPath(enabled: Boolean): Unit {
        builder.searchPath(enabled)
    }

    /**
     * Sets the positive execution timeout; defaults to three seconds.
     * Pool admission/acquisition and waiting behind another session caller are excluded.
     *
     * @throws IllegalArgumentException if [timeout] is zero or negative
     * @see MystemClientBuilder.requestTimeout
     */
    public fun requestTimeout(timeout: Duration): Unit {
        builder.requestTimeout(timeout)
    }

    /**
     * Sets the execution timeout after [toJavaDuration] conversion; defaults to three seconds.
     *
     * @throws IllegalArgumentException if [timeout] is zero or negative
     * @see MystemClientBuilder.requestTimeout
     */
    @JvmName("requestTimeoutKotlinDuration")
    public fun requestTimeout(timeout: KotlinDuration): Unit {
        requestTimeout(timeout.toJavaDuration())
    }

    /**
     * Sets the non-negative process I/O inactivity timeout for session/pool workers; zero disables it.
     * This also applies during silent active requests and is disabled by default.
     * One-shot requests, including file requests, do not use it.
     *
     * @throws IllegalArgumentException if [timeout] is negative
     * @see MystemClientBuilder.idleTimeout
     */
    public fun idleTimeout(timeout: Duration): Unit {
        builder.idleTimeout(timeout)
    }

    /**
     * Sets process I/O inactivity timeout after [toJavaDuration] conversion; zero disables it.
     *
     * @throws IllegalArgumentException if [timeout] is negative
     * @see MystemClientBuilder.idleTimeout
     */
    @JvmName("idleTimeoutKotlinDuration")
    public fun idleTimeout(timeout: KotlinDuration): Unit {
        idleTimeout(timeout.toJavaDuration())
    }

    /**
     * Uses one process and serializes requests. Requires JSON, `newLineEachWord(false)`,
     * and text requests without CR/LF. File requests still run in separate one-shot processes.
     */
    public fun session(): Unit {
        builder.session()
    }

    /**
     * Configures a pool of JSON-line processes. Requires JSON, `newLineEachWord(false)`,
     * and text requests without CR/LF. File requests still run in separate one-shot processes.
     * [configure] runs immediately with fresh defaults, replacing earlier pool settings.
     *
     * @throws IllegalArgumentException if the completed block contains invalid pool settings
     * @see MystemPoolOptionsDsl
     */
    public fun pooled(configure: MystemPoolOptionsDsl.() -> Unit): Unit {
        builder.pooled { pool -> MystemPoolOptionsDsl(pool).configure() }
    }

    /**
     * Selects pool mode, preserving earlier pool configuration or using defaults on a fresh builder.
     * Requires JSON, `newLineEachWord(false)`, and text requests without CR/LF.
     */
    public fun pooled(): Unit {
        builder.pooled()
    }

    /**
     * Selects pool mode with explicit options. Requires JSON, `newLineEachWord(false)`,
     * and text requests without CR/LF.
     */
    public fun pooled(options: MystemPoolOptions): Unit {
        builder.pooled(options)
    }

    /**
     * Sets a positive text payload limit in UTF-16 code units; defaults to 1,000,000.
     * The added protocol newline is excluded; exact-limit input is accepted. Does not limit input files.
     */
    public fun maxRequestChars(value: Int): Unit {
        builder.maxRequestChars(value)
    }

    /**
     * Sets a positive text payload limit in the configured encoding; defaults to 4,000,000 bytes.
     * The added protocol newline is excluded; exact-limit input is accepted. Does not limit input files.
     */
    public fun maxRequestBytes(value: Int): Unit {
        builder.maxRequestBytes(value)
    }

    /**
     * Sets the positive capture/protocol buffer bound; defaults to 32,000,000 bytes.
     * Includes line endings and bounds stderr/backlog too; does not limit direct file output.
     * Session and pool text requests discard excess stderr without failing; stdout limits still fail requests.
     */
    public fun maxResponseBytes(value: Int): Unit {
        builder.maxResponseBytes(value)
    }

    /**
     * Sets the positive captured stdout limit in UTF-16 code units; defaults to 8,000,000.
     * Includes line endings in every mode; does not limit direct file output.
     */
    public fun maxResponseChars(value: Int): Unit {
        builder.maxResponseChars(value)
    }

    /** Allows input in one-shot process-failure diagnostics; disabled by default. Does not redact MyStem stderr. */
    public fun includeInputInDiagnostics(enabled: Boolean): Unit {
        builder.includeInputInDiagnostics(enabled)
    }
}

/**
 * Mutable pool configuration receiver supplied to [MystemClientDsl.pooled].
 *
 * This receiver carries [MystemDslMarker], so client-level operations cannot be
 * called accidentally from inside `pooled { }`.
 * Setters store values; [MystemPoolOptions.Builder.build] validates them when the block returns.
 * This lets related settings such as [maxSize] and [warmupSize] be supplied in either order.
 * Do not configure the receiver concurrently. The resulting options are immutable.
 *
 * Kotlin durations are converted with [toJavaDuration] and have the same runtime validation
 * as their Java counterparts. Pool capacity applies to text requests; file requests start
 * separate one-shot processes and can exceed that process count.
 */
@MystemDslMarker
public class MystemPoolOptionsDsl internal constructor(
    private val builder: MystemPoolOptions.Builder,
) {
    /** Sets live-worker/admitted-request capacity in `1..256`; defaults to available processors, capped at 256. */
    public fun maxSize(value: Int): Unit {
        builder.maxSize(value)
    }

    /** Sets initial workers in `0..maxSize`; defaults to zero (lazy startup). */
    public fun warmupSize(value: Int): Unit {
        builder.warmupSize(value)
    }

    /** Sets the idle-worker target in `0..maxSize`; defaults to zero. */
    public fun minIdle(value: Int): Unit {
        builder.minIdle(value)
    }

    /**
     * Sets a positive timeout applied separately to FIFO admission and worker acquisition;
     * defaults to two seconds for each stage, before the request execution timeout starts.
     */
    public fun acquireTimeout(timeout: Duration): Unit {
        builder.acquireTimeout(timeout)
    }

    /** Sets a positive timeout for each acquisition stage as a Kotlin duration; defaults to two seconds. */
    @JvmName("acquireTimeoutKotlinDuration")
    public fun acquireTimeout(timeout: KotlinDuration): Unit {
        acquireTimeout(timeout.toJavaDuration())
    }

    /** Sets the positive worker health/reset hook timeout; defaults to two seconds. */
    public fun hookTimeout(timeout: Duration): Unit {
        builder.hookTimeout(timeout)
    }

    /** Sets the positive health/reset hook timeout as a Kotlin duration; defaults to two seconds. */
    @JvmName("hookTimeoutKotlinDuration")
    public fun hookTimeout(timeout: KotlinDuration): Unit {
        hookTimeout(timeout.toJavaDuration())
    }

    /** Sets a positive request count before worker replacement; defaults to [Int.MAX_VALUE]. */
    public fun maxRequestsPerWorker(value: Int): Unit {
        builder.maxRequestsPerWorker(value)
    }

    /**
     * Sets a non-negative worker rotation age; zero (the default) disables age-based replacement.
     * Does not interrupt an active request when the age is reached.
     */
    public fun maxWorkerAge(age: Duration): Unit {
        builder.maxWorkerAge(age)
    }

    /** Sets a non-negative worker rotation age as a Kotlin duration; zero (the default) disables it. */
    @JvmName("maxWorkerAgeKotlinDuration")
    public fun maxWorkerAge(age: KotlinDuration): Unit {
        maxWorkerAge(age.toJavaDuration())
    }

    /** Enables background idle-worker replenishment by default. */
    public fun backgroundReplenishment(enabled: Boolean): Unit {
        builder.backgroundReplenishment(enabled)
    }
}

/**
 * Mutable CLI options receiver supplied to [mystemOptions] or [MystemClientDsl.options].
 *
 * Each block starts with JSON, UTF-8, and all flags disabled. Boolean methods default to
 * enabling their option; pass `false` to disable it again within the same block. Validation
 * of dependent flags occurs after the block, so their call order does not matter.
 * Receivers are not thread-safe; the resulting [MystemOptions] is immutable.
 */
@MystemDslMarker
public class MystemOptionsDsl internal constructor(
    private val builder: MystemOptions.Builder,
) {
    /** Enables `-n`; only one-shot clients can use this option. */
    public fun newLineEachWord(enabled: Boolean = true): Unit {
        builder.newLineEachWord(enabled)
    }

    /** Enables MyStem input copying. */
    public fun copyInput(enabled: Boolean = true): Unit {
        builder.copyInput(enabled)
    }

    /** Keeps only dictionary words. */
    public fun dictionaryWordsOnly(enabled: Boolean = true): Unit {
        builder.dictionaryWordsOnly(enabled)
    }

    /** Emits lemmas without original word forms. */
    public fun lemmaOnly(enabled: Boolean = true): Unit {
        builder.lemmaOnly(enabled)
    }

    /** Emits MyStem grammar information. */
    public fun grammarInfo(enabled: Boolean = true): Unit {
        builder.grammarInfo(enabled)
    }

    /** Enables word-form merging; requires [grammarInfo] to be enabled. */
    public fun mergeWordForms(enabled: Boolean = true): Unit {
        builder.mergeWordForms(enabled)
    }

    /** Enables sentence markers; requires [copyInput] to be enabled. */
    public fun sentenceMarkers(enabled: Boolean = true): Unit {
        builder.sentenceMarkers(enabled)
    }

    /** Sets MyStem input/output encoding; defaults to UTF-8. */
    public fun encoding(encoding: MystemEncoding): Unit {
        builder.encoding(encoding)
    }

    /** Enables MyStem disambiguation. */
    public fun disambiguate(enabled: Boolean = true): Unit {
        builder.disambiguate(enabled)
    }

    /** Uses English grammar labels where MyStem supports them. */
    public fun englishGrammemes(enabled: Boolean = true): Unit {
        builder.englishGrammemes(enabled)
    }

    /** Sets a non-blank grammar filter; absent by default and validated when options are built. */
    public fun filterGrammar(value: String): Unit {
        builder.filterGrammar(value)
    }

    /**
     * Sets a custom dictionary path; client construction requires a readable regular file.
     * Building options does not read, modify, or take ownership of the file.
     *
     * @see MystemOptions.Builder.fixlist
     */
    public fun fixlist(path: Path): Unit {
        builder.fixlist(path)
    }

    /**
     * Converts [path] with [Path.of] and selects it as the custom dictionary.
     *
     * @throws java.nio.file.InvalidPathException if the string is not a valid path on this platform
     * @see fixlist
     */
    public fun fixlist(path: String): Unit {
        fixlist(Path.of(path))
    }

    /** Sets a MyStem fixlist path from a [File]. */
    public fun fixlist(file: File): Unit {
        fixlist(file.toPath())
    }

    /** Sets MyStem output format; defaults to JSON, which is required by session and pool clients. */
    public fun format(format: MystemOutputFormat): Unit {
        builder.format(format)
    }

    /** Asks MyStem to generate all hypotheses (`--generate-all`). */
    public fun generateAll(enabled: Boolean = true): Unit {
        builder.generateAll(enabled)
    }

    /** Asks MyStem to include lemma weights in JSON output. */
    public fun weight(enabled: Boolean = true): Unit {
        builder.weight(enabled)
    }
}

/**
 * Analyzes this text with [client], preserving its limits, framing restrictions, and exceptions.
 *
 * This is a synchronous call to [MystemClient.analyze]; it does not close the client,
 * preprocess the input, parse raw output, retry failures, or dispatch to another thread.
 * Built-in session and pool clients require text without CR or LF. Use one-shot mode for
 * multiline text, or prepare it before this call in a higher-level integration.
 *
 * @receiver text to analyze, possibly empty
 * @param client an open client, still owned by the caller after this call
 * @return raw MyStem output with the input text and successful-request statistics
 * @throws io.github.ulviar.mystem4j.MystemInvalidOptionsException if text exceeds payload limits
 * or violates the selected mode's line-framing restrictions
 * @throws io.github.ulviar.mystem4j.MystemClosedException if the client is closed
 * @throws io.github.ulviar.mystem4j.MystemException if execution, communication, or acquisition fails
 * @see MystemClient.analyze
 */
public fun String.analyzeWith(client: MystemClient): MystemRawResult = client.analyze(this)

/**
 * Analyzes this file and captures stdout through [client]. Built-in clients use a one-shot process.
 *
 * The file must be readable, regular, and encoded according to the client's options.
 * Text payload limits and session/pool single-line restrictions do not apply to its contents;
 * captured output remains subject to response limits. This synchronous call does not close
 * the client, modify or delete the input file, or parse the output.
 *
 * @receiver path to the input file; relative paths are allowed
 * @param client an open client, still owned by the caller after this call
 * @return captured raw output, input path, format, and successful-request statistics
 * @throws io.github.ulviar.mystem4j.MystemInvalidOptionsException if the path is not a readable regular file
 * @throws io.github.ulviar.mystem4j.MystemClosedException if the client is closed
 * @throws io.github.ulviar.mystem4j.MystemException if startup, execution, or output capture fails
 * @see MystemClient.analyzeFile
 */
public fun Path.analyzeWith(client: MystemClient): MystemFileContentResult = client.analyzeFile(this)

/**
 * Converts this file with [File.toPath] and delegates to [Path.analyzeWith].
 *
 * @receiver readable regular file in the client's configured encoding
 * @param client an open client, still owned by the caller after this call
 * @return captured raw output, input path, format, and successful-request statistics
 * @throws io.github.ulviar.mystem4j.MystemInvalidOptionsException if the file is not readable and regular
 * @throws io.github.ulviar.mystem4j.MystemClosedException if the client is closed
 * @throws io.github.ulviar.mystem4j.MystemException if startup, execution, or output capture fails
 * @see Path.analyzeWith
 */
public fun File.analyzeWith(client: MystemClient): MystemFileContentResult = toPath().analyzeWith(client)
