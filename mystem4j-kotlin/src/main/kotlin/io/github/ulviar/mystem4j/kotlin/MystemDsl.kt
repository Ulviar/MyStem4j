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

public fun mystemClient(configure: MystemClientDsl.() -> Unit): MystemClient {
    val builder = Mystem.builder()
    MystemClientDsl(builder).configure()
    return builder.build()
}

public fun mystemOptions(configure: MystemOptionsDsl.() -> Unit): MystemOptions {
    val builder = MystemOptions.builder()
    MystemOptionsDsl(builder).configure()
    return builder.build()
}

public class MystemClientDsl internal constructor(
    private val builder: MystemClientBuilder,
) {
    public fun executable(path: Path): Unit {
        builder.executable(path)
    }

    public fun executable(path: String): Unit {
        executable(Path.of(path))
    }

    public fun executable(file: File): Unit {
        executable(file.toPath())
    }

    public fun options(options: MystemOptions): Unit {
        builder.options(options)
    }

    public fun options(configure: MystemOptionsDsl.() -> Unit): Unit {
        options(mystemOptions(configure))
    }

    public fun searchPath(enabled: Boolean): Unit {
        builder.searchPath(enabled)
    }

    public fun requestTimeout(timeout: Duration): Unit {
        builder.requestTimeout(timeout)
    }

    @JvmName("requestTimeoutKotlinDuration")
    public fun requestTimeout(timeout: KotlinDuration): Unit {
        requestTimeout(timeout.toJavaDuration())
    }

    public fun idleTimeout(timeout: Duration): Unit {
        builder.idleTimeout(timeout)
    }

    @JvmName("idleTimeoutKotlinDuration")
    public fun idleTimeout(timeout: KotlinDuration): Unit {
        idleTimeout(timeout.toJavaDuration())
    }

    public fun session(): Unit {
        builder.session()
    }

    public fun pooled(configure: MystemPoolOptions.Builder.() -> Unit): Unit {
        builder.pooled { pool -> pool.configure() }
    }

    public fun pooled(): Unit {
        builder.pooled()
    }

    public fun pooled(options: MystemPoolOptions): Unit {
        builder.pooled(options)
    }

    public fun maxRequestChars(value: Int): Unit {
        builder.maxRequestChars(value)
    }

    public fun maxRequestBytes(value: Int): Unit {
        builder.maxRequestBytes(value)
    }

    public fun maxResponseBytes(value: Int): Unit {
        builder.maxResponseBytes(value)
    }

    public fun maxResponseChars(value: Int): Unit {
        builder.maxResponseChars(value)
    }

    public fun includeInputInDiagnostics(enabled: Boolean): Unit {
        builder.includeInputInDiagnostics(enabled)
    }
}

public class MystemOptionsDsl internal constructor(
    private val builder: MystemOptions.Builder,
) {
    public fun newLineEachWord(enabled: Boolean = true): Unit {
        builder.newLineEachWord(enabled)
    }

    public fun copyInput(enabled: Boolean = true): Unit {
        builder.copyInput(enabled)
    }

    public fun dictionaryWordsOnly(enabled: Boolean = true): Unit {
        builder.dictionaryWordsOnly(enabled)
    }

    public fun lemmaOnly(enabled: Boolean = true): Unit {
        builder.lemmaOnly(enabled)
    }

    public fun grammarInfo(enabled: Boolean = true): Unit {
        builder.grammarInfo(enabled)
    }

    public fun mergeWordForms(enabled: Boolean = true): Unit {
        builder.mergeWordForms(enabled)
    }

    public fun sentenceMarkers(enabled: Boolean = true): Unit {
        builder.sentenceMarkers(enabled)
    }

    public fun encoding(encoding: MystemEncoding): Unit {
        builder.encoding(encoding)
    }

    public fun disambiguate(enabled: Boolean = true): Unit {
        builder.disambiguate(enabled)
    }

    public fun englishGrammemes(enabled: Boolean = true): Unit {
        builder.englishGrammemes(enabled)
    }

    public fun filterGrammar(value: String): Unit {
        builder.filterGrammar(value)
    }

    public fun fixlist(path: Path): Unit {
        builder.fixlist(path)
    }

    public fun fixlist(path: String): Unit {
        fixlist(Path.of(path))
    }

    public fun fixlist(file: File): Unit {
        fixlist(file.toPath())
    }

    public fun format(format: MystemOutputFormat): Unit {
        builder.format(format)
    }

    public fun generateAll(enabled: Boolean = true): Unit {
        builder.generateAll(enabled)
    }

    public fun weight(enabled: Boolean = true): Unit {
        builder.weight(enabled)
    }
}

public fun String.analyzeWith(client: MystemClient): MystemRawResult = client.analyze(this)

public fun Path.analyzeWith(client: MystemClient): MystemFileContentResult = client.analyzeFile(this)

public fun File.analyzeWith(client: MystemClient): MystemFileContentResult = toPath().analyzeWith(client)
