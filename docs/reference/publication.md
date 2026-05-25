# Publication Reference

MyStem4j publishes each module as a Maven artifact under group `io.github.ulviar.mystem4j`.

## Java Baseline

Published artifacts target Java 21.

## Maven Metadata

Each module publication provides:

- artifact name and description;
- project URL;
- developer metadata;
- Apache License, Version 2.0 metadata;
- SCM metadata;
- source JAR;
- Javadoc JAR.

Library JARs provide explicit JPMS descriptors. The Gradle plugin artifact keeps a stable
`Automatic-Module-Name` manifest entry because Gradle plugins are loaded through Gradle's plugin classpath.

## License Boundary

MyStem4j artifacts use the Apache License, Version 2.0.

The native MyStem binary is licensed separately by Yandex and is not bundled into MyStem4j artifacts.

## Release Blockers

The default project version is still `0.1.0-SNAPSHOT`; set a release version with `-Pmystem4j.version=<version>` before publishing release artifacts.

Maven Central release also still needs signing and repository credentials.

Run real MyStem integration checks before release:

```bash
./gradlew realMystemTest -Dmystem4j.executable=/path/to/mystem
```

Run the exhaustive Unicode offset stress gate when changing Unicode preprocessing or MyStem offset alignment:

```bash
./gradlew realMystemUnicodeStress -Dmystem4j.executable=/path/to/mystem
```
