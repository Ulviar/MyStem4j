/** HTTP client for remote MyStem execution. */
module io.github.ulviar.mystem4j.http {
    requires transitive io.github.ulviar.mystem4j;
    requires java.net.http;
    requires com.fasterxml.jackson.core;
    exports io.github.ulviar.mystem4j.http;
}
