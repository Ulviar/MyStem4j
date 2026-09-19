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
 * Prevents accidental receiver leakage between nested MyStem4j DSL blocks.
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
 * @param configure configuration applied once before client creation
 * @return a client owned by the caller
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
 * @param configure configuration applied once before option validation
 * @return validated options
 */
public fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions {
    val builder = MystemOptions.builder()
    MystemOptionsDsl(builder).configure()
    return builder.build()
}

/**
 * Kotlin DSL facade for [MystemClientBuilder].
 */
@MystemDslMarker
public class MystemClientDsl internal constructor(
    private val builder: MystemClientBuilder,
) {
    /** Sets the MyStem executable path. */
    public fun executable(path: Path): Unit {
        builder.executable(path)
    }

    /** Sets the MyStem executable path from a string. */
    public fun executable(path: String): Unit {
        executable(Path.of(path))
    }

    /** Sets the MyStem executable path from a [File]. */
    public fun executable(file: File): Unit {
        executable(file.toPath())
    }

    /** Sets already built MyStem CLI options. */
    public fun options(options: MystemOptions): Unit {
        builder.options(options)
    }

    /** Configures MyStem CLI options inline. */
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
     */
    public fun requestTimeout(timeout: Duration): Unit {
        builder.requestTimeout(timeout)
    }

    /** Sets the positive execution timeout as a Kotlin duration; defaults to three seconds. */
    @JvmName("requestTimeoutKotlinDuration")
    public fun requestTimeout(timeout: KotlinDuration): Unit {
        requestTimeout(timeout.toJavaDuration())
    }

    /**
     * Sets the non-negative process I/O inactivity timeout for session/pool workers; zero disables it.
     * This also applies during silent active requests and is disabled by default.
     */
    public fun idleTimeout(timeout: Duration): Unit {
        builder.idleTimeout(timeout)
    }

    /** Sets the process I/O inactivity timeout as a Kotlin duration; zero (the default) disables it. */
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
 * Kotlin DSL facade for [MystemPoolOptions.Builder].
 *
 * This receiver carries [MystemDslMarker], so client-level operations cannot be
 * called accidentally from inside `pooled { }`.
 */
@MystemDslMarker
public class MystemPoolOptionsDsl internal constructor(
    private val builder: MystemPoolOptions.Builder,
) {
    /** Sets positive live-worker/admitted-request capacity; defaults to available processors. */
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
 * Kotlin DSL facade for [MystemOptions.Builder].
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

    /** Sets a custom dictionary path; client construction requires a readable regular file. */
    public fun fixlist(path: Path): Unit {
        builder.fixlist(path)
    }

    /** Sets a MyStem fixlist path from a string. */
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

    /** Asks MyStem to generate all possible forms. */
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
 * Does not close the client or parse its raw output.
 */
public fun String.analyzeWith(client: MystemClient): MystemRawResult = client.analyze(this)

/**
 * Analyzes this file and captures stdout through [client]. Built-in clients use a one-shot process.
 * Does not close the client or take ownership of the file.
 */
public fun Path.analyzeWith(client: MystemClient): MystemFileContentResult = client.analyzeFile(this)

/** Delegates file analysis to the [Path.analyzeWith] extension without closing [client]. */
public fun File.analyzeWith(client: MystemClient): MystemFileContentResult = toPath().analyzeWith(client)
