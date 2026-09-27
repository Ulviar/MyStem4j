/** HTTP server for remote MyStem execution. */
module io.github.ulviar.mystem4j.server {
    requires transitive io.github.ulviar.mystem4j;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.http;
    requires org.eclipse.jetty.io;
    requires org.eclipse.jetty.util;
    requires com.fasterxml.jackson.core;
    exports io.github.ulviar.mystem4j.server;
}
