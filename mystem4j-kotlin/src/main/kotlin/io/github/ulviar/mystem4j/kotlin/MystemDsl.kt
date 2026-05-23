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
import java.nio.file.Path
import java.time.Duration

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
    public fun executable(path: Path) {
        builder.executable(path)
    }

    public fun options(options: MystemOptions) {
        builder.options(options)
    }

    public fun searchPath(enabled: Boolean) {
        builder.searchPath(enabled)
    }

    public fun requestTimeout(timeout: Duration) {
        builder.requestTimeout(timeout)
    }

    public fun idleTimeout(timeout: Duration) {
        builder.idleTimeout(timeout)
    }

    public fun session() {
        builder.session()
    }

    public fun pooled(configure: MystemPoolOptions.Builder.() -> Unit) {
        builder.pooled { pool -> pool.configure() }
    }

    public fun pooled() {
        builder.pooled()
    }

    public fun pooled(options: MystemPoolOptions) {
        builder.pooled(options)
    }

    public fun maxRequestChars(value: Int) {
        builder.maxRequestChars(value)
    }

    public fun maxRequestBytes(value: Int) {
        builder.maxRequestBytes(value)
    }

    public fun maxResponseBytes(value: Int) {
        builder.maxResponseBytes(value)
    }

    public fun maxResponseChars(value: Int) {
        builder.maxResponseChars(value)
    }
}

public class MystemOptionsDsl internal constructor(
    private val builder: MystemOptions.Builder,
) {
    public fun newLineEachWord(enabled: Boolean = true) {
        builder.newLineEachWord(enabled)
    }

    public fun copyInput(enabled: Boolean = true) {
        builder.copyInput(enabled)
    }

    public fun dictionaryWordsOnly(enabled: Boolean = true) {
        builder.dictionaryWordsOnly(enabled)
    }

    public fun lemmaOnly(enabled: Boolean = true) {
        builder.lemmaOnly(enabled)
    }

    public fun grammarInfo(enabled: Boolean = true) {
        builder.grammarInfo(enabled)
    }

    public fun mergeWordForms(enabled: Boolean = true) {
        builder.mergeWordForms(enabled)
    }

    public fun sentenceMarkers(enabled: Boolean = true) {
        builder.sentenceMarkers(enabled)
    }

    public fun encoding(encoding: MystemEncoding) {
        builder.encoding(encoding)
    }

    public fun disambiguate(enabled: Boolean = true) {
        builder.disambiguate(enabled)
    }

    public fun englishGrammemes(enabled: Boolean = true) {
        builder.englishGrammemes(enabled)
    }

    public fun filterGrammar(value: String) {
        builder.filterGrammar(value)
    }

    public fun fixlist(path: Path) {
        builder.fixlist(path)
    }

    public fun format(format: MystemOutputFormat) {
        builder.format(format)
    }

    public fun generateAll(enabled: Boolean = true) {
        builder.generateAll(enabled)
    }

    public fun weight(enabled: Boolean = true) {
        builder.weight(enabled)
    }
}

public fun String.analyzeWith(client: MystemClient): MystemRawResult = client.analyze(this)

public fun Path.analyzeWith(client: MystemClient): MystemFileContentResult = client.analyzeFile(this)
