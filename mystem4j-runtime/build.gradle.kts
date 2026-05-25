plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation("com.github.ulviar:icli:0.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test>().configureEach {
    val realMystemExecutable = providers.systemProperty("mystem4j.executable")
        .map { rootProject.file(it).absolutePath }
        .orElse("")
    inputs.property("mystem4j.executable", realMystemExecutable)
    systemProperty("mystem4j.executable", realMystemExecutable.get())
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
