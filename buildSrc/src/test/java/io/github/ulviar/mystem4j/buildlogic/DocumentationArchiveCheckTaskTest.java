package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationArchiveCheckTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsJavadocNavigationToMissingTreePages() throws IOException {
        Path archive = archive("missing-trees.jar", Map.of(
                "index.html", "<a href=overview-tree.html>Tree</a>",
                "example/Example.html", "<a href='package-tree.html'>Tree</a>"));

        String message = assertThrows(GradleException.class,
                () -> DocumentationArchiveCheckTask.verifyArchives(List.of(archive.toFile()))).getMessage();

        assertTrue(message.contains("missing-trees.jar!/index.html"), message);
        assertTrue(message.contains("missing overview-tree.html"), message);
        assertTrue(message.contains("example/Example.html"), message);
        assertTrue(message.contains("missing example/package-tree.html"), message);
    }

    @Test
    void acceptsRelativeEncodedAnchoredAndExternalLinks() throws IOException {
        Path archive = archive("valid.jar", Map.of(
                "index.html", """
                        <a href="example/Example.html?tab=api&amp;view=full#method()">Class</a>
                        <a href="">Empty</a><a href="#top">Anchor</a><a href="?view=full">Query</a>
                        <a href="https://example.test/missing.html">External</a>
                        <a href="mailto:maintainer@example.test">Mail</a>
                        <a href="//example.test/missing.html">Scheme-relative</a>
                        <a href="jrt:/java.base/java/lang/String.class">Other scheme</a>
                        <!-- <a href="missing-comment.html">not an anchor</a> -->
                        <script>const example = '<a href="missing-script.html">';</script>
                        """,
                "example/Example.html", """
                        <A title="href='misleading.html'" HREF='../overview-tree.html#tree'>Tree</A>
                        <a href=./package-tree.html>Package tree</a>
                        <a href="../assets/A%20B%2B%C3%A9.txt?x=1&amp;y=2#part">Encoded</a>
                        <a href="../assets/A&amp;B.txt">HTML entity</a>
                        <a href='../assets/A+B.txt'>Literal plus</a>
                        <a href='../assets/Hash%23Question%3F.txt'>Encoded delimiters</a>
                        <a href="../example/../index.html">Normalized</a>
                        <a href="../">Directory index</a>
                        """,
                "overview-tree.html", "<a href='index.html'>Home</a>",
                "example/package-tree.html", "<a href='../index.html'>Home</a>",
                "assets/A B+é.txt", "asset",
                "assets/A&B.txt", "asset",
                "assets/A+B.txt", "asset",
                "assets/Hash#Question?.txt", "asset"));

        assertDoesNotThrow(() -> DocumentationArchiveCheckTask.verifyArchives(List.of(archive.toFile())));
    }

    @Test
    void rejectsPathsEscapingArchiveAndMalformedEscapes() throws IOException {
        Path archive = archive("invalid-targets.jar", Map.of("index.html", """
                <a href="../outside.html">Outside</a>
                <a href="%2e%2e/outside.html">Encoded outside</a>
                <a href="broken%2.html">Bad encoding</a>
                <a href="/index.html">Origin root</a>
                """));

        String message = assertThrows(GradleException.class,
                () -> DocumentationArchiveCheckTask.verifyArchives(List.of(archive.toFile()))).getMessage();

        assertTrue(message.contains("target escapes the archive root"), message);
        assertTrue(message.contains("%2e%2e/outside.html"), message);
        assertTrue(message.contains("broken%2.html"), message);
        assertTrue(message.contains("origin-root link is not portable"), message);
    }

    @Test
    void requiresArchivesHtmlAndRootIndex() throws IOException {
        assertThrows(GradleException.class, () -> DocumentationArchiveCheckTask.verifyArchives(List.of()));
        Path noHtml = archive("no-html.jar", Map.of("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));
        Path noIndex = archive("no-index.jar", Map.of("example/Example.html", "<p>API</p>"));

        String message = assertThrows(GradleException.class, () -> DocumentationArchiveCheckTask.verifyArchives(
                List.of(noHtml.toFile(), noIndex.toFile()))).getMessage();

        assertTrue(message.contains("archive contains no HTML pages"), message);
        assertTrue(message.contains("missing root index.html"), message);
    }

    @Test
    void boundsFailureExamples() throws IOException {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("index.html", "<p>API</p>");
        for (int index = 0; index < 25; index++) {
            pages.put("Example" + index + ".html", "<a href='missing.html'>Missing</a>");
        }
        Path archive = archive("many-errors.jar", pages);

        String message = assertThrows(GradleException.class,
                () -> DocumentationArchiveCheckTask.verifyArchives(List.of(archive.toFile()))).getMessage();

        assertTrue(message.contains("failed (25)"), message);
        assertTrue(message.contains("5 further failures omitted"), message);
    }

    private Path archive(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }
}
