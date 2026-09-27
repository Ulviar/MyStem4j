plugins {
    id("io.github.ulviar.mystem4j.java-conventions")
    `java-library`
    `maven-publish`
    id("io.github.ulviar.mystem4j.publishing-conventions")
    application
}

mystem4jJava { automaticModuleName.set("io.github.ulviar.mystem4j.server") }
mystem4jPublishing { moduleDescription.set("MyStem4j HTTP server.") }

val distributionLogging = configurations.create("distributionLogging") {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

dependencies {
    api(project(":mystem4j-runtime"))
    implementation(libs.jackson.core)
    implementation(libs.jetty.server)
    add(distributionLogging.name, libs.jetty.logging)
    testRuntimeOnly(libs.jetty.logging)
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

val standaloneLogging = distributionLogging - configurations.runtimeClasspath.get()

// An embedded library must not choose the application's SLF4J provider.
// Only the executable distribution and Gradle run task install Jetty's console logger.
tasks.named<CreateStartScripts>("startScripts") {
    classpath = files(classpath, standaloneLogging)
}
tasks.named<JavaExec>("run") { classpath += standaloneLogging }
distributions { main { contents { from(standaloneLogging) { into("lib") } } } }
