module io.github.ulviar.mystem4j.lucene {
    requires io.github.ulviar.mystem4j.model;
    requires transitive io.github.ulviar.mystem4j;
    requires transitive io.github.ulviar.mystem4j.tokenization;
    requires transitive org.apache.lucene.core;

    exports io.github.ulviar.mystem4j.lucene;
}
