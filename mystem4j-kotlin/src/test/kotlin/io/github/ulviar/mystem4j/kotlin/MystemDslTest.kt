package io.github.ulviar.mystem4j.kotlin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith
import io.github.ulviar.mystem4j.MystemOutputFormat
import io.github.ulviar.mystem4j.MystemPoolOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class MystemDslTest {
    @TempDir
    lateinit var temporaryDirectory: Path

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

    @Test
    fun buildsClientAndRunsStringExtension() {
        mystemClient {
            executable(fakeMystem())
            includeInputInDiagnostics(true)
            maxRequestChars(128)
            maxRequestBytes(512)
            maxResponseChars(1024)
            maxResponseBytes(2048)
        }.use { client ->
            val result = "мама".analyzeWith(client)

            assertEquals("[{\"text\":\"мама\"}]\n", result.output())
        }
    }

    @Test
    fun configuresPooledBuilderAndRejectsMultilineJsonLineRequests() {
        mystemClient {
            executable(fakeMystem())
            pooled {
                maxSize(1)
                warmupSize(0)
                minIdle(0)
            }
        }.use { client ->
            assertFailsWith<RuntimeException> {
                "a\nb".analyzeWith(client)
            }
        }
    }

    @Test
    fun runsFileExtension() {
        val input = temporaryDirectory.resolve("input.txt")
        Files.writeString(input, "file", StandardCharsets.UTF_8)

        mystemClient {
            executable(fakeMystem())
        }.use { client ->
            assertEquals("file", input.analyzeWith(client).output())
        }
    }

    private fun fakeMystem(): Path {
        val executable = temporaryDirectory.resolve("fake-mystem")
        Files.writeString(
            executable,
            """
            #!/bin/sh
            last=""
            previous=""
            for argument in "${'$'}@"; do
              previous="${'$'}last"
              last="${'$'}argument"
            done
            if [ -n "${'$'}last" ] && [ -f "${'$'}last" ]; then
              cat "${'$'}last"
              exit 0
            fi
            while IFS= read -r input || [ -n "${'$'}input" ]; do
              printf '[{"text":"%s"}]\n' "${'$'}input"
            done
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        executable.toFile().setExecutable(true, false)
        return executable
    }
}
