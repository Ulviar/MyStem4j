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

## Quality Gates

`check` includes:

- `jpmsSmokeTest`;
- `publicationMetadataCheck`;
- `apiSurfaceCheck`.

Run JMH wiring without a full benchmark run:

```bash
./gradlew :mystem4j-benchmarks:jmhCompileCheck
```

Run a short benchmark smoke test:

```bash
./gradlew :mystem4j-benchmarks:jmhSmoke
```

Run lightweight memory-retention smoke tests:

```bash
./gradlew memorySmokeTest
```

Run local release gates and publish artifacts into `build/release-dry-run-repo`:

```bash
./gradlew releaseCandidateCheck
```

Update the API baseline only after reviewing intentional public API changes:

```bash
./gradlew apiSurfaceCheck -Pmystem4j.updateApiBaseline=true
```

## License Boundary

MyStem4j artifacts use the Apache License, Version 2.0.

The native MyStem binary is licensed separately by Yandex and is not bundled into MyStem4j artifacts.

## Release Checklist

The default project version is `0.1.0`.

Maven Central release also still needs signing and repository credentials.

Run real MyStem integration checks before release:

```bash
./gradlew realMystemTest -Dmystem4j.executable=/path/to/mystem
```

Run the exhaustive Unicode offset stress gate when changing Unicode preprocessing or MyStem offset alignment:

```bash
./gradlew realMystemUnicodeStress -Dmystem4j.executable=/path/to/mystem
```
