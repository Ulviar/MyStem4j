package io.github.ulviar.mystem4j.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiSurfaceCheckTaskTest {
    @TempDir Path directory;

    @Test
    void snapshotsAndComparisonsAreIndependentOfHostAndCheckoutLineEndings() throws Exception {
        Path source = directory.resolve("Api.java");
        Files.writeString(source, "public final class Api { private Api() {} public static int answer() { return 42; } }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", directory.toString(), source.toString()));
        try (var jar = new JarOutputStream(Files.newOutputStream(directory.resolve("api.jar")))) {
            jar.putNextEntry(new JarEntry("Api.class"));
            Files.copy(directory.resolve("Api.class"), jar);
            jar.closeEntry();
        }
        // A manifest classpath avoids command-line length limits on Windows.
        var locations = new LinkedHashSet<String>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            locations.add(Path.of(entry).toAbsolutePath().toUri().toASCIIString());
        }
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) locations.add(url.toExternalForm());
            }
        }
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, String.join(" ", locations));
        Path classpath = directory.resolve("classpath.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(classpath), manifest)) {
            jar.flush();
        }
        for (String separator : List.of("\n", "\r\n")) {
            ProcessSupport.run(List.of(tool("java"), "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "-Dline.separator=" + separator, "-cp", classpath.toString(),
                    Fixture.class.getName(), directory.toString()), directory.toFile(), Duration.ofSeconds(30));
        }
    }

    private static String tool(String name) {
        return Path.of(System.getProperty("java.home"), "bin", name
                + (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? ".exe" : ""))
                .toString();
    }

    public static final class Fixture {
        private Fixture() {}

        public static void main(String[] args) {
            try {
                verify(Path.of(args[0]));
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
            // ProjectBuilder owns background services; this isolated fixture JVM owns their lifetime.
            System.exit(0);
        }

        private static void verify(Path root) throws Exception {
            var project = ProjectBuilder.builder().withProjectDir(root.resolve("project").toFile()).build();
            var task = project.getTasks().register("apiCheck", ApiSurfaceCheckTask.class).get();
            Path baseline = root.resolve("baseline");
            task.getBaselineDirectory().set(baseline.toFile());
            task.getReportDirectory().set(root.resolve("report").toFile());
            task.getJarPathByProject().set(Map.of("fixture", root.resolve("api.jar").toString()));
            task.getBuilderOnlyConfigurationClasses().set(List.of());
            task.getJavapExecutable().set(tool("javap"));
            task.getUpdateBaseline().set(true);
            task.checkApiSurface();
            String expected = "# fixture\n\n## Api\nCompiled from \"Api.java\"\n"
                    + "public final class Api {\n  public static int answer();\n}\n";
            Path file = baseline.resolve("fixture.txt");
            if (!expected.equals(Files.readString(file))) throw new AssertionError("Snapshot must use canonical LF");
            task.getUpdateBaseline().set(false);
            for (String text : List.of(expected, expected.replace("\n", "\r\n"))) {
                Files.writeString(file, text);
                task.checkApiSurface();
            }
            Files.writeString(file, expected.replace("answer", "oldName"));
            try {
                task.checkApiSurface();
                throw new AssertionError("A real API change must still fail");
            } catch (GradleException expectedFailure) {
                if (!expectedFailure.getMessage().contains("Public API surface changed")) throw expectedFailure;
            }
            if (!expected.equals(Files.readString(root.resolve("report/fixture.actual.txt")))) {
                throw new AssertionError("Failure report must use canonical LF");
            }
        }
    }
}
