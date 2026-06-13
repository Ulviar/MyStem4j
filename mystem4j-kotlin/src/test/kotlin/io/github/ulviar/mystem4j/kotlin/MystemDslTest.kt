package io.github.ulviar.mystem4j.kotlin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import io.github.ulviar.mystem4j.MystemInvalidOptionsException
import io.github.ulviar.mystem4j.MystemOutputFormat
import io.github.ulviar.mystem4j.MystemPoolOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
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

        assertEquals(true, options.grammarInfo())
        assertEquals(true, options.disambiguate())
        assertEquals(true, options.englishGrammemes())
        assertEquals(MystemOutputFormat.JSON, options.format())
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
    fun preservesJavaOptionValidation() {
        assertFailsWith<MystemInvalidOptionsException> {
            mystemOptions {
                mergeWordForms()
            }
        }
        assertFailsWith<MystemInvalidOptionsException> {
            mystemOptions {
                sentenceMarkers()
            }
        }
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
    fun buildsClientWithNestedOptionsAndKotlinFriendlyOverloads() {
        val executable = fakeMystem().toFile()
        val fixlist = temporaryDirectory.resolve("fixlist.txt").toFile()
        Files.writeString(fixlist.toPath(), "мама мама", StandardCharsets.UTF_8)

        mystemClient {
            executable(executable.absolutePath)
            options {
                grammarInfo()
                fixlist(fixlist)
                format(MystemOutputFormat.JSON)
            }
            requestTimeout(2.seconds)
            idleTimeout(0.seconds)
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
            assertEquals("file", input.toFile().analyzeWith(client).output())
        }
    }

    private fun fakeMystem(): Path {
        val executable = temporaryDirectory.resolve("fake-mystem${executableSuffix()}")
        Files.writeString(executable, launcher(), StandardCharsets.UTF_8)
        if (!isWindows()) {
            executable.toFile().setExecutable(true, false)
        }
        return executable
    }

    private fun launcher(): String {
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaExecutableName())
        val classpath = System.getProperty("java.class.path")
        val mainClass = FakeMystemProcess::class.java.name
        return if (isWindows()) {
            "@echo off\r\n\"$javaExecutable\" -cp \"$classpath\" $mainClass %*\r\nexit /b %ERRORLEVEL%\r\n"
        } else {
            "#!/bin/sh\nexec ${shellQuote(javaExecutable.toString())} -cp ${shellQuote(classpath)} $mainClass \"${'$'}@\"\n"
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun executableSuffix(): String = if (isWindows()) ".cmd" else ""

    private fun javaExecutableName(): String = if (isWindows()) "java.exe" else "java"

    private fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")
}
