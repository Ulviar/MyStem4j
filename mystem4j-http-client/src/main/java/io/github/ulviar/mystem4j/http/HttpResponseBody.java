package io.github.ulviar.mystem4j.http;

import io.github.ulviar.mystem4j.MystemOutputLimitException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** A cancellable, bounded body sink; it never buffers a file download in memory. */
final class HttpResponseBody implements HttpResponse.BodyHandler<byte[]>, HttpResponse.BodySubscriber<byte[]>, AutoCloseable {
    private final long limit;
    private final Path file;
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private OutputStream output;
    private Flow.Subscription subscription;
    private long count;
    private long effectiveLimit;
    private boolean closed;

    HttpResponseBody(long limit, Path file) { this.limit = limit; this.file = file; }

    @Override
    public synchronized HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo info) {
        if (!closed) {
            try {
                effectiveLimit = info.statusCode() == 200 ? limit : Math.min(limit, 16_384);
                output = info.statusCode() == 200 && file != null
                        ? Files.newOutputStream(file) : new ByteArrayOutputStream();
            } catch (IOException failure) { fail(failure); }
        }
        return this;
    }

    @Override
    public CompletionStage<byte[]> getBody() { return result; }

    @Override
    public synchronized void onSubscribe(Flow.Subscription value) {
        if (closed || subscription != null) { value.cancel(); return; }
        subscription = value;
        subscription.request(1);
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> buffers) {
        if (closed) return;
        try {
            byte[] chunk = new byte[8192];
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > effectiveLimit - count) {
                    throw new MystemOutputLimitException("HTTP response exceeds configured byte limit");
                }
                count += buffer.remaining();
                while (buffer.hasRemaining()) {
                    int size = Math.min(buffer.remaining(), chunk.length);
                    buffer.get(chunk, 0, size);
                    output.write(chunk, 0, size);
                }
            }
            subscription.request(1);
        } catch (IOException | RuntimeException failure) { fail(failure); }
    }

    @Override
    public synchronized void onError(Throwable error) { fail(error); }

    @Override
    public synchronized void onComplete() {
        if (closed) return;
        try {
            output.close();
            closed = true;
            result.complete(output instanceof ByteArrayOutputStream bytes ? bytes.toByteArray() : new byte[0]);
        } catch (IOException failure) { fail(failure); }
    }

    private void fail(Throwable error) {
        closed = true;
        if (subscription != null) subscription.cancel();
        if (output != null) {
            try { output.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
        }
        result.completeExceptionally(error);
    }

    @Override
    public synchronized void close() {
        if (!closed) fail(new IOException("HTTP request cancelled"));
    }
}
