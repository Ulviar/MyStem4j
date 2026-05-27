package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class PublicationMetadataCheckTask extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getModuleNameByProject();

    @Input
    public abstract MapProperty<String, String> getJarPathByProject();

    @Input
    public abstract MapProperty<String, String> getPomPathByProject();

    @Input
    public abstract Property<String> getPluginPomPath();

    @Input
    public abstract Property<String> getProjectUrl();

    @Input
    public abstract MapProperty<String, String> getDependencyScopesByProject();

    @TaskAction
    public void checkMetadata() {
        Map<String, String> moduleNames = getModuleNameByProject().get();
        for (Map.Entry<String, String> entry : moduleNames.entrySet()) {
            String projectName = entry.getKey();
            String moduleName = entry.getValue();
            File jarFile = new File(getJarPathByProject().get().get(projectName));
            String descriptor = ProcessSupport.run(
                    java.util.List.of("jar", "--describe-module", "--file", jarFile.getAbsolutePath()),
                    jarFile.getParentFile());
            if (descriptor.lines().findFirst().filter(line -> line.startsWith(moduleName)).isEmpty()) {
                throw new GradleException("Unexpected JPMS descriptor for " + projectName + ": " + descriptor);
            }
        }

        for (String projectName : getPomPathByProject().get().keySet()) {
            requireMetadata(projectName, read(getPomPathByProject().get().get(projectName)));
        }
        requireMetadata("mystem4j-gradle-plugin", read(getPluginPomPath().get()));

        for (Map.Entry<String, String> entry : getDependencyScopesByProject().get().entrySet()) {
            String projectName = entry.getKey();
            String pom = read(getPomPathByProject().get().get(projectName));
            for (String dependency : entry.getValue().split(",")) {
                String spec = dependency.strip();
                if (spec.isEmpty()) {
                    continue;
                }
                int separator = spec.lastIndexOf(':');
                if (separator <= 0 || separator == spec.length() - 1) {
                    throw new GradleException("Invalid dependency scope spec for " + projectName + ": " + spec);
                }
                String artifactId = spec.substring(0, separator);
                String scope = spec.substring(separator + 1);
                requireDependencyScope(projectName, pom, artifactId, scope);
            }
        }
    }

    private void requireMetadata(String projectName, String pom) {
        if (!pom.contains("<name>" + projectName + "</name>")) {
            throw new GradleException("Missing publication name for " + projectName + ".");
        }
        if (!pom.contains("<name>Apache License, Version 2.0</name>")) {
            throw new GradleException("Missing Apache 2.0 metadata for " + projectName + ".");
        }
        if (!pom.contains("<url>" + getProjectUrl().get() + "</url>")) {
            throw new GradleException("Missing project URL for " + projectName + ".");
        }
    }

    private static void requireDependencyScope(String projectName, String pom, String artifactId, String scope) {
        Pattern pattern = Pattern.compile(
                "<dependency>.*?<artifactId>\\Q" + artifactId + "\\E</artifactId>.*?<scope>\\Q" + scope
                        + "\\E</scope>.*?</dependency>",
                Pattern.DOTALL);
        if (!pattern.matcher(pom).find()) {
            throw new GradleException(
                    "Expected " + projectName + " POM to expose " + artifactId + " with " + scope + " scope.");
        }
    }

    private static String read(String path) {
        try {
            return Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new GradleException("Failed to read " + path, error);
        }
    }
}
