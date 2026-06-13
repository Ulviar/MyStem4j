package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class MarkdownLocalLinksCheckTask extends DefaultTask {
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[[^\\]]+]\\(([^)]+)\\)");
    private final ConfigurableFileCollection markdownFiles;

    @Inject
    public MarkdownLocalLinksCheckTask(ObjectFactory objects) {
        markdownFiles = objects.fileCollection();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getMarkdownFiles() {
        return markdownFiles;
    }

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void checkLinks() {
        ArrayList<String> missing = new ArrayList<>();
        File projectDirectory = getProjectDirectory().get().getAsFile();
        for (File file : markdownFiles.getFiles().stream().sorted().toList()) {
            String text = read(file);
            Matcher matcher = LINK_PATTERN.matcher(text);
            while (matcher.find()) {
                String rawTarget = matcher.group(1);
                String targetWithoutAnchor = rawTarget.split("#", 2)[0];
                if (isExternalOrAnchorOnly(targetWithoutAnchor)) {
                    continue;
                }
                File target = file.getParentFile().toPath().resolve(targetWithoutAnchor).normalize().toFile();
                if (!target.exists()) {
                    missing.add(relative(projectDirectory, file) + ": " + rawTarget);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new GradleException("Markdown local link check failed:\n" + String.join("\n", missing));
        }
    }

    private static boolean isExternalOrAnchorOnly(String target) {
        return target.isBlank()
                || target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:");
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Failed to read " + file, error);
        }
    }

    private static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString();
    }
}
