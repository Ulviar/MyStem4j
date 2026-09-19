package example;

import io.github.ulviar.mystem4j.Mystem;
import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemOptions;
import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalyzer;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.QueryBuilder;

public final class LuceneSearchExample {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass the path to the MyStem executable.");
        }
        try (MystemClient client = Mystem.builder()
                .executable(Path.of(args[0]))
                .options(MystemOptions.builder()
                        .format(MystemOutputFormat.JSON)
                        .grammarInfo(true)
                        .disambiguate(true)
                        .build())
                .pooled()
                .build()) {
            int matches = indexAndSearch(client);
            if (matches != 1) {
                throw new IllegalStateException("Expected one matching document, got " + matches);
            }
            System.out.println("Matches: " + matches);
        }
    }

    public static int indexAndSearch(MystemClient client) throws IOException {
        try (Analyzer analyzer = new MystemLuceneAnalyzer(client);
                ByteBuffersDirectory directory = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                Document document = new Document();
                document.add(new TextField("body", "Мама мыла раму.", Field.Store.NO));
                writer.addDocument(document);
            }

            Query query = new QueryBuilder(analyzer).createBooleanQuery("body", "мыть");
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                return new IndexSearcher(reader).count(query);
            }
        }
    }
}
