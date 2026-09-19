package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.StringReader;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class PublicationMetadataCheckTask extends DefaultTask {
    private static final long LATEST_REPRODUCIBLE_TIMESTAMP = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli();

    @Input
    public abstract MapProperty<String, String> getModuleNameByProject();

    @Input
    public abstract MapProperty<String, String> getJarPathByProject();

    @Classpath
    public abstract ConfigurableFileCollection getJarFiles();

    @Input
    public abstract MapProperty<String, String> getPomPathByProject();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getPomFiles();

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
            requireReproducibleTimestamps(projectName, jarFile);
            String descriptor = describeModule(jarFile);
            if (descriptor.lines().findFirst().filter(line -> line.startsWith(moduleName)).isEmpty()) {
                throw new GradleException("Unexpected JPMS descriptor for " + projectName + ": " + descriptor);
            }
        }

        for (String projectName : getPomPathByProject().get().keySet()) {
            requireMetadata(projectName, read(getPomPathByProject().get().get(projectName)));
        }
        requireMetadata("mystem4j-gradle-plugin", read(getPluginPomPath().get()));
        requireDependencyArtifacts("mystem4j-gradle-plugin", read(getPluginPomPath().get()), Set.of());

        for (Map.Entry<String, String> entry : getDependencyScopesByProject().get().entrySet()) {
            String projectName = entry.getKey();
            String pom = read(getPomPathByProject().get().get(projectName));
            Set<String> expectedArtifacts = new java.util.HashSet<>();
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
                expectedArtifacts.add(artifactId);
                requireDependencyScope(projectName, pom, artifactId, scope);
            }
            requireDependencyArtifacts(projectName, pom, expectedArtifacts);
        }
    }

    private static void requireReproducibleTimestamps(String projectName, File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            if (!zip.entries().hasMoreElements()) {
                throw new GradleException("Published JAR for " + projectName + " is empty: " + jarFile);
            }
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                long timestamp = entry.getTime();
                if (timestamp < 0 || timestamp >= LATEST_REPRODUCIBLE_TIMESTAMP) {
                    throw new GradleException("Published JAR for " + projectName
                            + " contains a non-reproducible timestamp in " + entry.getName() + ".");
                }
            }
        } catch (java.io.IOException error) {
            throw new GradleException("Failed to inspect published JAR " + jarFile, error);
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

    static void requireDependencyScope(String projectName, String pom, String artifactId, String scope) {
        if (!scope.equals(dependencyScopes(projectName, pom).get(artifactId))) {
            throw new GradleException(
                    "Expected " + projectName + " POM to declare " + artifactId + " with " + scope + " scope.");
        }
    }

    static void requireDependencyArtifacts(String projectName, String pom, Set<String> expected) {
        Set<String> actual = dependencyScopes(projectName, pom).keySet();
        if (!actual.equals(expected)) {
            throw new GradleException(projectName + " POM dependencies differ: expected="
                    + new java.util.TreeSet<>(expected) + ", actual=" + new java.util.TreeSet<>(actual));
        }
    }

    private static Map<String, String> dependencyScopes(String projectName, String pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element project = factory.newDocumentBuilder().parse(new InputSource(new StringReader(pom)))
                    .getDocumentElement();
            Map<String, String> scopes = new java.util.HashMap<>();
            for (Element dependencies : children(project, "dependencies")) {
                for (Element dependency : children(dependencies, "dependency")) {
                    String artifactId = childText(dependency, "artifactId");
                    String scope = childText(dependency, "scope");
                    if (scopes.put(artifactId, scope.isEmpty() ? "compile" : scope) != null) {
                        throw new GradleException(projectName + " POM has ambiguous dependency " + artifactId);
                    }
                }
            }
            return scopes;
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException | java.io.IOException error) {
            throw new GradleException("Failed to parse " + projectName + " POM.", error);
        }
    }

    private static java.util.List<Element> children(Element parent, String name) {
        var result = new java.util.ArrayList<Element>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(name)) {
                result.add(element);
            }
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        return children(parent, name).stream().findFirst().map(Element::getTextContent).orElse("").strip();
    }

    private static String read(String path) {
        try {
            return Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new GradleException("Failed to read " + path, error);
        }
    }

    private static String describeModule(File jarFile) {
        ToolProvider jarTool = ToolProvider.findFirst("jar")
                .orElseThrow(() -> new GradleException("Current JDK does not provide the jar tool."));
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        int exitCode = jarTool.run(
                new PrintWriter(output),
                new PrintWriter(error),
                "--describe-module",
                "--file",
                jarFile.getAbsolutePath());
        if (exitCode != 0) {
            throw new GradleException("jar --describe-module failed for " + jarFile + "\n" + error);
        }
        return output.toString();
    }
}
