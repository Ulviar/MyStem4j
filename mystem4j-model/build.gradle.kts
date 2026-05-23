plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":mystem4j-runtime"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
