package io.github.ulviar.mystem4j.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

public final class Mystem4jPublishingConventionsPlugin implements Plugin<Project> {
    private static final String DEFAULT_PROJECT_URL = "https://github.com/Ulviar/MyStem4j";

    @Override
    public void apply(Project project) {
        Mystem4jPublishingConventionsExtension extension = project.getExtensions()
                .create("mystem4jPublishing", Mystem4jPublishingConventionsExtension.class);
        extension.getProjectUrl().convention(DEFAULT_PROJECT_URL);
        extension.getModuleDescription().convention("MyStem4j module.");
        project.getPluginManager().withPlugin("maven-publish", ignored -> configurePublishing(project, extension));
    }

    private static void configurePublishing(Project project, Mystem4jPublishingConventionsExtension extension) {
        project.getExtensions().configure(PublishingExtension.class, publishing -> {
            publishing.getPublications().withType(MavenPublication.class).configureEach(publication -> {
                publication.getPom().getName().set(project.getName());
                publication.getPom().getDescription().set(extension.getModuleDescription());
                publication.getPom().getUrl().set(extension.getProjectUrl());
                publication.getPom().developers(developers -> developers.developer(developer -> {
                    developer.getId().set("ulviar");
                    developer.getName().set("Ulviar");
                }));
                publication.getPom().licenses(licenses -> licenses.license(license -> {
                    license.getName().set("Apache License, Version 2.0");
                    license.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0.txt");
                }));
                publication.getPom().scm(scm -> {
                    scm.getConnection().set(extension.getProjectUrl().map(url -> "scm:git:" + url + ".git"));
                    scm.getDeveloperConnection().set(extension.getProjectUrl().map(url -> "scm:git:" + url + ".git"));
                    scm.getUrl().set(extension.getProjectUrl());
                });
            });
            publishing.getRepositories().maven(repository -> {
                repository.setName("releaseDryRun");
                repository.setUrl(project.getRootProject()
                        .getLayout()
                        .getBuildDirectory()
                        .dir("release-dry-run-repo")
                        .get()
                        .getAsFile()
                        .toURI());
            });
        });
    }
}
