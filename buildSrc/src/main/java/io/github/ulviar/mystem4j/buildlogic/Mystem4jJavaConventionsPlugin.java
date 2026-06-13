package io.github.ulviar.mystem4j.buildlogic;

import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

public final class Mystem4jJavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Mystem4jJavaConventionsExtension extension = project.getExtensions()
                .create("mystem4jJava", Mystem4jJavaConventionsExtension.class);
        project.getDependencyLocking().lockAllConfigurations();
        project.getPluginManager().apply("jacoco");
        project.getPlugins().withType(JavaPlugin.class).configureEach(plugin -> configureJavaProject(project, extension));
    }

    private static void configureJavaProject(Project project, Mystem4jJavaConventionsExtension extension) {
        project.getExtensions().configure(JavaPluginExtension.class, java -> {
            java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));
            java.withJavadocJar();
            java.withSourcesJar();
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getRelease().set(21);
            task.getOptions().setEncoding("UTF-8");
        });

        project.getTasks().withType(Jar.class).configureEach(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            task.getInputs().property("mystem4j.automaticModuleName", extension.getAutomaticModuleName());
            task.doFirst(ignored -> {
                if (extension.getAutomaticModuleName().isPresent()) {
                    task.getManifest()
                            .attributes(Map.of("Automatic-Module-Name", extension.getAutomaticModuleName().get()));
                }
            });
        });

        project.getTasks().withType(Javadoc.class).configureEach(task -> {
            task.getOptions().setEncoding("UTF-8");
            ((StandardJavadocDocletOptions) task.getOptions()).addStringOption("Xdoclint:all,-missing", "-quiet");
        });

        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);

        project.getExtensions()
                .configure(JacocoPluginExtension.class, jacoco -> jacoco.setToolVersion(jacocoVersion(project)));
        project.getTasks().named("jacocoTestReport", JacocoReport.class).configure(report -> {
            report.dependsOn(project.getTasks().named("test"));
            report.getReports().getXml().getRequired().set(true);
            report.getReports().getHtml().getRequired().set(true);
            report.getReports().getCsv().getRequired().set(false);
        });
    }

    private static String jacocoVersion(Project project) {
        return project.getExtensions()
                .getByType(VersionCatalogsExtension.class)
                .named("libs")
                .findVersion("jacoco")
                .orElseThrow(() -> new IllegalStateException("Missing libs.versions.jacoco entry."))
                .getRequiredVersion();
    }
}
