plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
    application
}

mystem4jJava { automaticModuleName.set("io.github.ulviar.mystem4j.server") }
mystem4jPublishing { moduleDescription.set("MyStem4j HTTP server.") }

dependencies {
    api(project(":mystem4j-runtime"))
    implementation(libs.jackson.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(project(":mystem4j-http-client"))
    testImplementation(project(":mystem4j-lucene"))
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}

application { mainClass.set("io.github.ulviar.mystem4j.server.MystemServerMain") }
tasks.withType<Test>().configureEach {
    val executable = providers.systemProperty("mystem4j.executable").orElse("")
    inputs.property("mystem4j.executable", executable)
    systemProperty("mystem4j.executable", executable.get())
}
