package consumer;

import io.github.ulviar.mystem4j.http.MystemHttpClient;
import java.net.URI;
import java.time.Duration;

public final class Smoke {
    public static void main(String[] args) {
        MystemHttpClient.builder(URI.create("https://localhost/mystem/"))
                .connectTimeout(Duration.ofSeconds(2)).maxRequestBytes(1024).bearerToken("example");
    }
}
