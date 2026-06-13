package io.github.ulviar.mystem4j.kotlin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal object FakeMystemProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        if (copyInputFileToStdout(args.toList())) {
            return
        }
        val input = System.`in`.readAllBytes().toString(StandardCharsets.UTF_8)
        print("""[{"text":"${escapeJson(input)}"}]""" + "\n")
    }

    private fun copyInputFileToStdout(arguments: List<String>): Boolean {
        if (arguments.isEmpty()) {
            return false
        }
        val input = Path.of(arguments.last())
        if (!input.isRegularFile()) {
            return false
        }
        Files.copy(input, System.out)
        return true
    }

    private fun escapeJson(text: String): String =
        buildString(text.length) {
            for (value in text) {
                when (value) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (value < ' ') {
                            append("\\u")
                            append(value.code.toString(16).padStart(4, '0'))
                        } else {
                            append(value)
                        }
                }
            }
        }
}
