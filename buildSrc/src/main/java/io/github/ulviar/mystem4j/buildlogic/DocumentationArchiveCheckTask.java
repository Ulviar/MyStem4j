package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Verification has no output artifacts.")
public abstract class DocumentationArchiveCheckTask extends DefaultTask {
    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDocumentationJars();

    @TaskAction
    public void checkDocumentation() {
        verifyArchives(getDocumentationJars().getFiles());
    }

    static void verifyArchives(Collection<File> archives) {
        Failures failures = new Failures();
        if (archives.isEmpty()) {
            failures.add("No documentation JARs were supplied.");
        }
        for (File archive : archives.stream().sorted().toList()) {
            verifyArchive(archive, failures);
        }
        failures.throwIfPresent();
    }

    private static void verifyArchive(File archive, Failures failures) {
        try (ZipFile zip = new ZipFile(archive)) {
            Set<String> files = new HashSet<>();
            List<String> pages = new ArrayList<>();
            for (ZipEntry entry : zip.stream().filter(entry -> !entry.isDirectory()).toList()) {
                files.add(entry.getName());
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".html")) {
                    pages.add(entry.getName());
                }
            }
            if (pages.isEmpty()) {
                failures.add(archive + ": archive contains no HTML pages.");
            } else if (!files.contains("index.html")) {
                failures.add(archive + ": missing root index.html.");
            }
            for (String page : pages.stream().sorted().toList()) {
                String html;
                try (var input = zip.getInputStream(zip.getEntry(page))) {
                    html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                inspectPage(archive, page, html, files, failures);
            }
        } catch (IOException error) {
            failures.add(archive + ": cannot read documentation archive: " + error.getMessage());
        }
    }

    private static void inspectPage(File archive, String page, String html, Set<String> files, Failures failures)
            throws IOException {
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                checkAnchor(tag, attributes);
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                checkAnchor(tag, attributes);
            }

            private void checkAnchor(HTML.Tag tag, MutableAttributeSet attributes) {
                Object attribute = attributes.getAttribute(HTML.Attribute.HREF);
                if (tag != HTML.Tag.A || attribute == null) {
                    return;
                }
                String href = attribute.toString().strip();
                try {
                    String target = localTarget(page, href);
                    if (target != null && !files.contains(target)) {
                        failures.add(archive + "!/" + page + ": href=\"" + href + "\" resolves to missing " + target);
                    }
                } catch (IllegalArgumentException error) {
                    failures.add(archive + "!/" + page + ": invalid href=\"" + href + "\": " + error.getMessage());
                }
            }
        }, true);
    }

    private static String localTarget(String page, String href) {
        if (href.startsWith("//") || URI_SCHEME.matcher(href).find()) {
            return null;
        }
        if (href.startsWith("/")) {
            throw new IllegalArgumentException("origin-root link is not portable within a hosted documentation archive");
        }
        int end = href.length();
        for (char delimiter : new char[] {'?', '#'}) {
            int index = href.indexOf(delimiter);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        if (end == 0) {
            return null;
        }
        // URLDecoder uses form rules; protect literal '+' because it is a valid path character.
        String path = URLDecoder.decode(href.substring(0, end).replace("+", "%2B"), StandardCharsets.UTF_8)
                .replace('\\', '/');
        String parent = page.substring(0, page.lastIndexOf('/') + 1);
        String combined = parent + path;
        ArrayDeque<String> segments = new ArrayDeque<>();
        for (String segment : combined.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("target escapes the archive root");
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        if (path.endsWith("/") || segments.isEmpty()) {
            segments.addLast("index.html");
        }
        return String.join("/", segments);
    }

    private static final class Failures {
        private static final int MAX_EXAMPLES = 20;
        private static final int MAX_EXAMPLE_LENGTH = 600;
        private final List<String> examples = new ArrayList<>();
        private int count;

        void add(String failure) {
            count++;
            if (examples.size() < MAX_EXAMPLES) {
                String singleLine = failure.replace('\n', ' ').replace('\r', ' ');
                examples.add(singleLine.length() <= MAX_EXAMPLE_LENGTH
                        ? singleLine : singleLine.substring(0, MAX_EXAMPLE_LENGTH) + "...");
            }
        }

        void throwIfPresent() {
            if (count > 0) {
                String remainder = count > examples.size()
                        ? "\n... " + (count - examples.size()) + " further failures omitted." : "";
                throw new GradleException("Documentation archive check failed (" + count + "):\n"
                        + String.join("\n", examples) + remainder
                        + "\nRegenerate documentation with its linked pages, or correct the local links before packaging.");
            }
        }
    }
}
