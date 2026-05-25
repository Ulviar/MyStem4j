# Publication Reference

MyStem4j publishes each module as a Maven artifact under group `io.github.ulviar.mystem4j`.

## Java Baseline

Published artifacts target Java 21.

## Maven Metadata

Each module publication provides:

- artifact name and description;
- project URL;
- developer metadata;
- SCM metadata;
- source JAR;
- Javadoc JAR.

Each JAR also has a stable `Automatic-Module-Name` manifest entry.

## Release Blockers

Before publishing outside local/internal repositories, choose and add the project license:

- add a root `LICENSE` file;
- add matching Maven POM license metadata;
- document how that project license relates to the separately licensed native MyStem binary.

Maven Central release also still needs signing and repository credentials.
