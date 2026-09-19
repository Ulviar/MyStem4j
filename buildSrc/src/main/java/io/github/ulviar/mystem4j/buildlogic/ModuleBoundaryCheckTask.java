package io.github.ulviar.mystem4j.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Checks module boundaries without producing an artifact.")
public abstract class ModuleBoundaryCheckTask extends DefaultTask {
    @Input
    public abstract Property<String> getComponent();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLibraryJar();

    @Classpath
    public abstract ConfigurableFileCollection getConsumerCompileClasspath();

    @Classpath
    public abstract ConfigurableFileCollection getConsumerRuntimeClasspath();

    @TaskAction
    public void checkBoundaries() throws IOException {
        String component = getComponent().get();
        Path classes = getTemporaryDir().toPath().resolve("classes");
        if (Files.exists(classes)) {
            try (var paths = Files.walk(classes)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(classes);
        var descriptors = ModuleFinder.of(getLibraryJar().get().getAsFile().toPath()).findAll();
        if (descriptors.size() != 1) {
            throw new GradleException(component + " JAR must define one module");
        }
        ModuleBoundaryPolicy.checkDescriptor(component, descriptors.iterator().next().descriptor());
        try (ZipFile jar = new ZipFile(getLibraryJar().get().getAsFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                if (entry.getName().equals("module-info.class")) {
                    continue;
                }
                Path output = classes.resolve(entry.getName()).normalize();
                if (!output.startsWith(classes)) {
                    throw new GradleException("Invalid class entry: " + entry.getName());
                }
                Files.createDirectories(output.getParent());
                try (var input = jar.getInputStream(entry)) {
                    Files.copy(input, output);
                }
            }
        }
        ModuleBoundaryPolicy.checkDependencies(component, dependencies(classes, false), false);
        ModuleBoundaryPolicy.checkDependencies(component, dependencies(classes, true), true);
        if (!component.equals("gradle-plugin")) {
            ModuleBoundaryPolicy.checkClasspath(component, modules(getConsumerCompileClasspath().getFiles()), true);
            ModuleBoundaryPolicy.checkClasspath(component, modules(getConsumerRuntimeClasspath().getFiles()), false);
        }
        getLogger().lifecycle("Verified {} module boundaries.", component);
    }

    static String dependencies(Path classes, boolean apiOnly) {
        ToolProvider jdeps = ToolProvider.findFirst("jdeps")
                .orElseThrow(() -> new GradleException("The current JDK must provide jdeps."));
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        var arguments = new java.util.ArrayList<>(java.util.List.of("-verbose:class", "-filter:none"));
        if (apiOnly) {
            arguments.add("--api-only");
        }
        arguments.add(classes.toString());
        int exitCode = jdeps.run(new PrintWriter(output), new PrintWriter(error), arguments.toArray(String[]::new));
        if (exitCode != 0) {
            throw new GradleException("jdeps failed:\n" + output + error);
        }
        return output.toString();
    }

    private static Set<String> modules(Set<File> files) {
        return ModuleFinder.of(files.stream().map(File::toPath).toArray(Path[]::new)).findAll().stream()
                .map(reference -> reference.descriptor().name()).collect(Collectors.toSet());
    }
}
