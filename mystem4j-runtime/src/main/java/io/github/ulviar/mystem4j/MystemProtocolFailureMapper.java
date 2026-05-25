package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.command.CommandExecutionException;
import com.github.ulviar.icli.session.ProtocolSessionException;
import com.github.ulviar.icli.session.ProtocolTranscript;
import com.github.ulviar.icli.session.PooledProtocolSessionException;

final class MystemProtocolFailureMapper {
    private MystemProtocolFailureMapper() {}

    static MystemException map(CommandExecutionException error, String context) {
        String message = context + ": " + error.getMessage();
        return switch (error.reason()) {
            case LAUNCH_FAILED, READINESS_FAILED, READINESS_TIMEOUT -> new MystemStartupException(message, error);
            case DECODE_ERROR, RUNTIME_FAILURE -> new MystemProtocolException(message, error);
        };
    }

    static MystemException map(ProtocolSessionException error) {
        return switch (error.reason()) {
            case TIMEOUT -> new MystemRequestTimeoutException(error.getMessage());
            case CLOSED -> new MystemClosedException(error.getMessage());
            case REQUEST_TOO_LARGE -> new MystemInvalidOptionsException(error.getMessage());
            case RESPONSE_TOO_LARGE, OUTPUT_BACKLOG_OVERFLOW -> new MystemOutputLimitException(error.getMessage());
            case PROCESS_EXITED -> new MystemProcessException(
                    messageWithSafeTranscript(error),
                    error.exitCode(),
                    stderrFromTranscript(error.transcript()),
                    error);
            case EOF, BROKEN_PIPE, DECODE_ERROR, PROTOCOL_DECODER_FAILED, FAILURE ->
                    new MystemProtocolException(messageWithSafeTranscript(error), error);
        };
    }

    static MystemException map(PooledProtocolSessionException error) {
        return switch (error.reason()) {
            case ACQUIRE_TIMEOUT -> new MystemPoolExhaustedException(error.getMessage(), error);
            case CLOSED -> new MystemClosedException(error.getMessage());
            case STARTUP_FAILED -> new MystemStartupException(error.getMessage(), error);
            case HOOK_TIMEOUT, WORKER_FAILED -> new MystemProtocolException(error.getMessage(), error);
        };
    }

    private static String messageWithSafeTranscript(ProtocolSessionException error) {
        ProtocolTranscript transcript = error.transcript();
        String stderr = stderrFromTranscript(transcript);
        String flags = transcriptFlags(transcript);
        if (stderr.isBlank() && flags.isBlank()) {
            return error.getMessage();
        }
        StringBuilder message = new StringBuilder(error.getMessage());
        if (!flags.isBlank()) {
            message.append(". transcript ").append(flags);
        }
        if (!stderr.isBlank()) {
            message.append(". stderr: ").append(stderr);
        }
        return message.toString();
    }

    private static String stderrFromTranscript(ProtocolTranscript transcript) {
        StringBuilder stderr = new StringBuilder();
        for (String line : transcript.text().split("\\R")) {
            if (line.startsWith("stderr: ")) {
                if (!stderr.isEmpty()) {
                    stderr.append('\n');
                }
                stderr.append(line.substring("stderr: ".length()));
            }
        }
        return trim(stderr.toString());
    }

    private static String transcriptFlags(ProtocolTranscript transcript) {
        StringBuilder flags = new StringBuilder();
        if (transcript.truncated()) {
            flags.append("[truncated] ");
        }
        if (transcript.malformed()) {
            flags.append("[malformed] ");
        }
        if (transcript.redacted()) {
            flags.append("[redacted] ");
        }
        return flags.toString().trim();
    }

    private static String trim(String text) {
        if (text == null || text.length() <= 2_000) {
            return text == null ? "" : text;
        }
        return text.substring(0, 2_000);
    }
}
