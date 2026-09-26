/** HTTP server for remote MyStem execution. */
module io.github.ulviar.mystem4j.server {
    requires transitive io.github.ulviar.mystem4j;
    requires jdk.httpserver;
    requires com.fasterxml.jackson.core;
    exports io.github.ulviar.mystem4j.server;
}
