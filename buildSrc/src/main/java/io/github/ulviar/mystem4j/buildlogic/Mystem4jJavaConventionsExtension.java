package io.github.ulviar.mystem4j.buildlogic;

import org.gradle.api.provider.Property;

public abstract class Mystem4jJavaConventionsExtension {
    public abstract Property<String> getAutomaticModuleName();
}
