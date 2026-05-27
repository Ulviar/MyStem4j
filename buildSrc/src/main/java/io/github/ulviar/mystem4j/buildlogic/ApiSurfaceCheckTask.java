package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class ApiSurfaceCheckTask extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getJarPathByProject();

    @InputDirectory
    public abstract DirectoryProperty getBaselineDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getReportDirectory();

    @Input
    public abstract Property<Boolean> getUpdateBaseline();

    @Input
    public abstract Property<String> getJavapExecutable();

    @TaskAction
    public void checkApiSurface() {
        File baselineDirectory = getBaselineDirectory().get().getAsFile();
        File reportDirectory = getReportDirectory().get().getAsFile();
        if (getUpdateBaseline().get()) {
            baselineDirectory.mkdirs();
        }
        reportDirectory.mkdirs();

        for (Map.Entry<String, String> entry : getJarPathByProject().get().entrySet()) {
            String projectName = entry.getKey();
            String actual = publicApi(projectName, new File(entry.getValue()));
            File baselineFile = new File(baselineDirectory, projectName + ".txt");
            if (getUpdateBaseline().get()) {
                write(baselineFile, actual);
                continue;
            }
            if (!baselineFile.isFile()) {
                throw new GradleException("Missing API baseline for " + projectName
                        + ". Run with -Pmystem4j.updateApiBaseline=true after reviewing API.");
            }
            String expected = read(baselineFile);
            if (!expected.equals(actual)) {
                File actualFile = new File(reportDirectory, projectName + ".actual.txt");
                write(actualFile, actual);
                throw new GradleException("Public API surface changed for " + projectName
                        + ". Review " + actualFile.toPath() + " and update the baseline intentionally.");
            }
        }
    }

    private String publicApi(String projectName, File jarFile) {
        StringBuilder api = new StringBuilder();
        api.append("# ").append(projectName).append(System.lineSeparator()).append(System.lineSeparator());
        try (ZipFile zip = new ZipFile(jarFile)) {
            zip.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !"module-info.class".equals(name))
                    .filter(name -> !name.endsWith("/package-info.class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .sorted(Comparator.naturalOrder())
                    .forEach(className -> appendPublicClass(api, jarFile, className));
        } catch (IOException error) {
            throw new GradleException("Failed to inspect " + jarFile, error);
        }
        return api.toString().stripTrailing() + System.lineSeparator();
    }

    private void appendPublicClass(StringBuilder api, File jarFile, String className) {
        String output = ProcessSupport.run(
                        java.util.List.of(
                                getJavapExecutable().get(), "-public", "-classpath", jarFile.getAbsolutePath(), className),
                        jarFile.getParentFile())
                .replace("\r\n", "\n")
                .strip();
        boolean hasPublicDeclaration =
                output.lines().anyMatch(line -> line.startsWith("public ") || line.startsWith("protected "));
        if (hasPublicDeclaration) {
            api.append("## ").append(className).append(System.lineSeparator());
            api.append(output).append(System.lineSeparator()).append(System.lineSeparator());
        }
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Failed to read " + file, error);
        }
    }

    private static void write(File file, String text) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Failed to write " + file, error);
        }
    }
}
