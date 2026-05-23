package io.github.ulviar.mystem4j.kotlin

import io.github.ulviar.mystem4j.MystemOutputFormat
import io.github.ulviar.mystem4j.MystemPoolOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class MystemDslTest {
    @Test
    fun buildsOptions() {
        val options =
            mystemOptions {
                grammarInfo()
                disambiguate()
                englishGrammemes()
                format(MystemOutputFormat.JSON)
            }

        assertEquals(
            listOf("-i", "-e", "utf-8", "-d", "--eng-gr", "--format", "json"),
            options.toArguments(),
        )
    }

    @Test
    fun buildsPoolOptions() {
        val options =
            MystemPoolOptions.builder()
                .maxSize(2)
                .warmupSize(1)
                .minIdle(1)
                .build()

        assertEquals(2, options.maxSize())
        assertEquals(1, options.warmupSize())
        assertEquals(1, options.minIdle())
    }
}
