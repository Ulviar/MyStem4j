package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class JpmsSmokeTestTask extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getModulePath();

    @OutputDirectory
    public abstract DirectoryProperty getWorkDirectory();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    public abstract Property<String> getJavacExecutable();

    @TaskAction
    public void runSmokeTest() {
        File workDirectory = getWorkDirectory().get().getAsFile();
        Path sourceDirectory = workDirectory.toPath().resolve("src");
        Path classesDirectory = workDirectory.toPath().resolve("classes");
        Path moduleDirectory = sourceDirectory.resolve("io.github.ulviar.mystem4j.smoke");
        Path packageDirectory = moduleDirectory.resolve("io/github/ulviar/mystem4j/smoke");
        try {
            deleteRecursively(workDirectory.toPath());
            Files.createDirectories(packageDirectory);
            Files.writeString(
                    moduleDirectory.resolve("module-info.java"),
                    """
                    module io.github.ulviar.mystem4j.smoke {
                        requires io.github.ulviar.mystem4j;
                        requires io.github.ulviar.mystem4j.model;
                        requires io.github.ulviar.mystem4j.tokenization;
                        requires io.github.ulviar.mystem4j.lucene;
                        requires io.github.ulviar.mystem4j.kotlin;
                    }
                    """.stripIndent(),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    packageDirectory.resolve("Smoke.java"),
                    """
                    package io.github.ulviar.mystem4j.smoke;

                    import io.github.ulviar.mystem4j.MystemOptions;
                    import io.github.ulviar.mystem4j.MystemOutputFormat;
                    import io.github.ulviar.mystem4j.kotlin.MystemDslKt;
                    import io.github.ulviar.mystem4j.lucene.MystemLuceneAnalysisOptions;
                    import io.github.ulviar.mystem4j.model.MystemDocument;
                    import io.github.ulviar.mystem4j.model.MystemJsonParser;
                    import io.github.ulviar.mystem4j.tokenization.MystemSearchTokenizer;

                    public final class Smoke {
                        private Smoke() {
                        }

                        public static void main(String[] args) {
                            MystemOptions options = MystemOptions.builder()
                                    .format(MystemOutputFormat.JSON)
                                    .build();
                            MystemDocument document = new MystemJsonParser().parse(
                                    "Мамы",
                                    "[{\\"text\\":\\"Мамы\\",\\"analysis\\":[{\\"lex\\":\\"мама\\",\\"gr\\":\\"S\\"}]}]");
                            if (new MystemSearchTokenizer().tokenize(document).isEmpty()) {
                                throw new IllegalStateException("Tokenization produced no terms.");
                            }
                            if (MystemLuceneAnalysisOptions.defaults().maxChunkChars() <= 0) {
                                throw new IllegalStateException("Invalid Lucene defaults.");
                            }
                            if (options.format() != MystemOutputFormat.JSON) {
                                throw new IllegalStateException("Invalid runtime options.");
                            }
                            if (!MystemDslKt.class.getName().endsWith("MystemDslKt")) {
                                throw new IllegalStateException("Kotlin API is not readable from JPMS.");
                            }
                        }
                    }
                    """.stripIndent(),
                    StandardCharsets.UTF_8);
            Files.createDirectories(classesDirectory);
        } catch (IOException error) {
            throw new GradleException("Failed to prepare JPMS smoke sources.", error);
        }

        String modulePath = getModulePath().getFiles().stream()
                .map(File::getAbsolutePath)
                .distinct()
                .reduce((left, right) -> left + File.pathSeparator + right)
                .orElse("");
        ProcessSupport.run(
                List.of(
                        getJavacExecutable().get(),
                        "--module-path",
                        modulePath,
                        "-d",
                        classesDirectory.toAbsolutePath().toString(),
                        moduleDirectory.resolve("module-info.java").toAbsolutePath().toString(),
                        packageDirectory.resolve("Smoke.java").toAbsolutePath().toString()),
                workDirectory);
        ProcessSupport.run(
                List.of(
                        getJavaExecutable().get(),
                        "--module-path",
                        modulePath + File.pathSeparator + classesDirectory.toAbsolutePath(),
                        "--module",
                        "io.github.ulviar.mystem4j.smoke/io.github.ulviar.mystem4j.smoke.Smoke"),
                workDirectory);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            ArrayList<Path> sorted = new ArrayList<>();
            paths.forEach(sorted::add);
            sorted.sort(Comparator.reverseOrder());
            for (Path current : sorted) {
                Files.deleteIfExists(current);
            }
        }
    }
}
