package io.github.ulviar.mystem4j.buildlogic;

import org.gradle.api.provider.Property;

public abstract class Mystem4jPublishingConventionsExtension {
    public abstract Property<String> getModuleDescription();

    public abstract Property<String> getProjectUrl();
}
