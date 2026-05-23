package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.Icli;
import com.github.ulviar.icli.command.CapturePolicy;
import com.github.ulviar.icli.command.CommandExecutionException;
import com.github.ulviar.icli.command.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

final class OneShotMystemClient implements MystemClient {
    private final Path executable;
    private final MystemOptions options;
    private final Duration requestTimeout;
    private final int maxRequestChars;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final boolean includeInputInDiagnostics;
    private final AtomicBoolean closed = new AtomicBoolean();

    OneShotMystemClient(
            Path executable,
            MystemOptions options,
            Duration requestTimeout,
            int maxRequestChars,
            int maxRequestBytes,
            int maxResponseBytes,
            boolean includeInputInDiagnostics) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.options = Objects.requireNonNull(options, "options");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.maxRequestChars = maxRequestChars;
        this.maxRequestBytes = maxRequestBytes;
        this.maxResponseBytes = maxResponseBytes;
        this.includeInputInDiagnostics = includeInputInDiagnostics;
    }

    @Override
    public MystemRawResult analyze(String text) {
        ensureOpen();
        Objects.requireNonNull(text, "text");
        int inputBytes = text.getBytes(options.encoding().charset()).length;
        validateRequestSize(text.length(), inputBytes);

        CommandResult result;
        try {
            result = Icli.command(executable.toString())
                    .run()
                    .withArgs(options.toArguments())
                    .withInput(text, options.encoding().charset())
                    .withCharset(options.encoding().charset())
                    .withTimeout(requestTimeout)
                    .withCapture(CapturePolicy.bounded(maxResponseBytes))
                    .execute();
        } catch (CommandExecutionException error) {
            throw MystemProtocolFailureMapper.map(error, "Failed to execute MyStem one-shot process");
        }
        ensureSuccessful(result, "MyStem one-shot request failed", text);

        MystemRequestStats stats = MystemRequestStats.oneShotText(
                result.elapsed(), text.length(), inputBytes, result.stdout().length(), result.stdoutBytes().length);
        return new MystemRawResult(text, result.stdout(), options.format(), stats);
    }

    @Override
    public MystemFileContentResult analyzeFile(Path input) {
        ensureOpen();
        Path validatedInput = validateInputFile(input);
        CommandResult result = runFileCommand(List.of(validatedInput.toString()));
        ensureSuccessful(result, "MyStem file request failed", validatedInput.toString());

        MystemRequestStats stats = MystemRequestStats.oneShotFile(
                result.elapsed(),
                -1,
                fileSize(validatedInput),
                result.stdout().length(),
                result.stdoutBytes().length);
        return new MystemFileContentResult(validatedInput, result.stdout(), options.format(), stats);
    }

    @Override
    public MystemFileResult analyzeFile(Path input, Path output) {
        ensureOpen();
        Path validatedInput = validateInputFile(input);
        Path validatedOutput = validateOutputFile(output);
        validateDifferentFiles(validatedInput, validatedOutput);
        CommandResult result = runFileCommand(List.of(validatedInput.toString(), validatedOutput.toString()));
        ensureSuccessful(result, "MyStem file request failed", validatedInput + " -> " + validatedOutput);

        MystemRequestStats stats = MystemRequestStats.oneShotFile(
                result.elapsed(), -1, fileSize(validatedInput), -1, fileSize(validatedOutput));
        return new MystemFileResult(validatedInput, validatedOutput, options.format(), stats);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private CommandResult runFileCommand(List<String> fileArguments) {
        ArrayList<String> arguments = new ArrayList<>(options.toArguments());
        arguments.addAll(fileArguments);
        try {
            return Icli.command(executable.toString())
                    .run()
                    .withArgs(arguments)
                    .withCharset(options.encoding().charset())
                    .withTimeout(requestTimeout)
                    .withCapture(CapturePolicy.bounded(maxResponseBytes))
                    .execute();
        } catch (CommandExecutionException error) {
            throw MystemProtocolFailureMapper.map(error, "Failed to execute MyStem file process");
        }
    }

    private void validateRequestSize(int chars, int bytes) {
        if (chars > maxRequestChars) {
            throw new MystemInvalidOptionsException(
                    "MyStem request exceeds maxRequestChars: " + chars + " > " + maxRequestChars);
        }
        if (bytes > maxRequestBytes) {
            throw new MystemInvalidOptionsException(
                    "MyStem request exceeds maxRequestBytes: " + bytes + " > " + maxRequestBytes);
        }
    }

    private Path validateInputFile(Path input) {
        Objects.requireNonNull(input, "input");
        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new MystemInvalidOptionsException("Input file must be readable: " + input);
        }
        return input;
    }

    private Path validateOutputFile(Path output) {
        Objects.requireNonNull(output, "output");
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            throw new MystemInvalidOptionsException("Output file parent directory does not exist: " + parent);
        }
        if (parent != null && !Files.isWritable(parent)) {
            throw new MystemInvalidOptionsException("Output file parent directory is not writable: " + parent);
        }
        if (Files.isDirectory(output)) {
            throw new MystemInvalidOptionsException("Output file path points to a directory: " + output);
        }
        if (Files.exists(output) && !Files.isWritable(output)) {
            throw new MystemInvalidOptionsException("Output file is not writable: " + output);
        }
        return output;
    }

    private void validateDifferentFiles(Path input, Path output) {
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new MystemInvalidOptionsException("Input and output files must be different: " + input);
        }
        try {
            if (Files.exists(output) && Files.isSameFile(input, output)) {
                throw new MystemInvalidOptionsException("Input and output files must be different: " + input);
            }
        } catch (IOException error) {
            throw new MystemInvalidOptionsException("Could not compare input and output files: " + error.getMessage());
        }
    }

    private void ensureSuccessful(CommandResult result, String message, String diagnosticInput) {
        if (result.timedOut()) {
            throw new MystemRequestTimeoutException(message + ": timed out after " + requestTimeout);
        }
        if (result.stdoutTruncated() || result.stderrTruncated()) {
            throw new MystemOutputLimitException(message + ": output exceeded " + maxResponseBytes + " bytes");
        }
        if (!result.succeeded()) {
            OptionalInt exitCode = result.exitCode();
            String diagnostic = includeInputInDiagnostics ? " input=" + diagnosticInput : "";
            throw new MystemProcessException(
                    message + ": exitCode=" + (exitCode.isPresent() ? exitCode.getAsInt() : "unknown") + diagnostic,
                    exitCode,
                    trimStderr(result.stderr()));
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new MystemClosedException("MyStem client is closed.");
        }
    }

    private static long fileSize(Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : -1;
        } catch (IOException error) {
            return -1;
        }
    }

    private static String trimStderr(String stderr) {
        if (stderr == null || stderr.length() <= 2_000) {
            return stderr;
        }
        return stderr.substring(0, 2_000);
    }
}
