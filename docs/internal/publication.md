# Publication Notes

This page is for maintainers. User-facing artifact and API information lives in
`docs/reference`.

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

Library JARs provide explicit JPMS descriptors. The Gradle plugin artifact keeps a
stable `Automatic-Module-Name` manifest entry because Gradle plugins are loaded
through Gradle's plugin classpath.

## Release Gates

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

## Release Checklist

The default project version is `0.1.0`.

Before publishing MyStem4j artifacts to a public repository, make sure every
transitive runtime dependency, including `com.github.ulviar:icli:0.1.0`, is already
available from a repository that normal consumers can resolve.

Maven Central release also needs signing and repository credentials.

Run real MyStem integration checks before release:

```bash
./gradlew realMystemTest -Dmystem4j.executable=/path/to/mystem
```

Run the exhaustive Unicode offset stress gate when changing Unicode preprocessing
or MyStem offset alignment:

```bash
./gradlew realMystemUnicodeStress -Dmystem4j.executable=/path/to/mystem
```
