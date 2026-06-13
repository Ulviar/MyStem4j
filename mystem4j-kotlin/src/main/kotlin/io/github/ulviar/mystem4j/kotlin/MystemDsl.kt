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
 * rules. Close the returned client when the application no longer needs it.
 */
public fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient {
    val builder = Mystem.builder()
    MystemClientDsl(builder).configure()
    return builder.build()
}

/**
 * Builds MyStem CLI options with Kotlin receiver-style configuration.
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

    /** Enables or disables PATH lookup when no executable path is set. */
    public fun searchPath(enabled: Boolean): Unit {
        builder.searchPath(enabled)
    }

    /** Sets the per-request timeout as a Java [Duration]. */
    public fun requestTimeout(timeout: Duration): Unit {
        builder.requestTimeout(timeout)
    }

    /** Sets the per-request timeout as a Kotlin duration. */
    @JvmName("requestTimeoutKotlinDuration")
    public fun requestTimeout(timeout: KotlinDuration): Unit {
        requestTimeout(timeout.toJavaDuration())
    }

    /** Sets the idle timeout for session or pooled workers. */
    public fun idleTimeout(timeout: Duration): Unit {
        builder.idleTimeout(timeout)
    }

    /** Sets the idle timeout for session or pooled workers as a Kotlin duration. */
    @JvmName("idleTimeoutKotlinDuration")
    public fun idleTimeout(timeout: KotlinDuration): Unit {
        idleTimeout(timeout.toJavaDuration())
    }

    /** Uses one reusable JSON-line MyStem process. */
    public fun session(): Unit {
        builder.session()
    }

    /** Uses a pool of reusable JSON-line MyStem processes and configures it inline. */
    public fun pooled(configure: MystemPoolOptions.Builder.() -> Unit): Unit {
        builder.pooled { pool -> pool.configure() }
    }

    /** Uses a pool of reusable JSON-line MyStem processes with default pool options. */
    public fun pooled(): Unit {
        builder.pooled()
    }

    /** Uses a pool of reusable JSON-line MyStem processes with explicit pool options. */
    public fun pooled(options: MystemPoolOptions): Unit {
        builder.pooled(options)
    }

    /** Sets the maximum number of UTF-16 code units accepted in one text request. */
    public fun maxRequestChars(value: Int): Unit {
        builder.maxRequestChars(value)
    }

    /** Sets the maximum number of encoded bytes accepted in one text request. */
    public fun maxRequestBytes(value: Int): Unit {
        builder.maxRequestBytes(value)
    }

    /** Sets the maximum number of encoded bytes retained from MyStem output. */
    public fun maxResponseBytes(value: Int): Unit {
        builder.maxResponseBytes(value)
    }

    /** Sets the maximum number of decoded characters accepted from MyStem output. */
    public fun maxResponseChars(value: Int): Unit {
        builder.maxResponseChars(value)
    }

    /** Allows full input text in diagnostics when enabled. */
    public fun includeInputInDiagnostics(enabled: Boolean): Unit {
        builder.includeInputInDiagnostics(enabled)
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

    /** Enables MyStem word-form merging. */
    public fun mergeWordForms(enabled: Boolean = true): Unit {
        builder.mergeWordForms(enabled)
    }

    /** Emits sentence markers. */
    public fun sentenceMarkers(enabled: Boolean = true): Unit {
        builder.sentenceMarkers(enabled)
    }

    /** Sets the MyStem process encoding. */
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

    /** Sets MyStem grammar filtering expression. */
    public fun filterGrammar(value: String): Unit {
        builder.filterGrammar(value)
    }

    /** Sets a MyStem fixlist path. */
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

    /** Sets MyStem output format. */
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

/** Runs MyStem analysis for this string using [client]. */
public fun String.analyzeWith(client: MystemClient): MystemRawResult = client.analyze(this)

/** Runs MyStem file analysis for this path using [client]. */
public fun Path.analyzeWith(client: MystemClient): MystemFileContentResult = client.analyzeFile(this)

/** Runs MyStem file analysis for this file using [client]. */
public fun File.analyzeWith(client: MystemClient): MystemFileContentResult = toPath().analyzeWith(client)
