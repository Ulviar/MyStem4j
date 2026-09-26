package io.github.ulviar.mystem4j.lucene;

import static org.junit.Assume.assumeFalse;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import example.LuceneSearchExample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

@ThreadLeakFilters(defaultFilters = true, filters = ProcwrightSharedThreadsFilter.class)
public class MystemLuceneDocumentationExampleTest extends LuceneTestCase {
    public void testExampleIndexesAndQueriesLemmasWithinOneAnalyzerLifecycle() throws IOException {
        FakeMystemClient client = new FakeMystemClient(input -> switch (input) {
            case "Мама мыла раму." -> """
                    [
                      {"text":"Мама","analysis":[{"lex":"мама","gr":"S"}]},
                      {"text":"мыла","analysis":[{"lex":"мыть","gr":"V"}]},
                      {"text":"раму","analysis":[{"lex":"рама","gr":"S"}]}
                    ]
                    """;
            case "мыть" -> """
                    [{"text":"мыть","analysis":[{"lex":"мыть","gr":"V"}]}]
                    """;
            default -> throw new AssertionError("Unexpected request: " + input);
        });
        try (client) {
            assertEquals(1, LuceneSearchExample.indexAndSearch(client));
            assertEquals(List.of("Мама мыла раму.", "мыть"), client.requests());
            assertFalse("The caller owns the client.", client.isClosed());
        }
        assertEquals(1, client.closeCount());
    }

    public void testDocumentedJavaIsTheCompiledExample() throws IOException {
        String documentation = Files.readString(testInput("mystem4j.luceneExampleDoc")).replace("\r\n", "\n");
        String source = Files.readString(testInput("mystem4j.luceneExampleSource")).replace("\r\n", "\n");
        String startMarker = "<!-- lucene-search-example:start -->\n```java\n";
        String endMarker = "\n```\n<!-- lucene-search-example:end -->";
        int start = documentation.indexOf(startMarker);
        assertTrue("The complete example must have its start marker.", start >= 0);
        start += startMarker.length();
        int end = documentation.indexOf(endMarker, start);
        assertTrue("The complete example must have its end marker.", end >= start);
        assertEquals(source.strip(), documentation.substring(start, end).strip());
    }

    public void testDocumentedMainRunsWithRealMystem() throws IOException {
        String executable = System.getProperty("mystem4j.executable", "");
        assumeFalse("Set -Dmystem4j.executable=/path/to/mystem to run the complete example.", executable.isBlank());
        LuceneSearchExample.main(new String[] {executable});
    }

    private static Path testInput(String property) {
        String path = System.getProperty(property);
        assertNotNull("Gradle must declare the example input: " + property, path);
        return Path.of(path);
    }
}
