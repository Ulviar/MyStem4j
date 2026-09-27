# Publication Notes

This page is for maintainers. User-facing artifact and API information lives in
`docs/reference`.

## Java Baseline

Published artifacts, including the Gradle plugin, target Java 25. The JVM running
Gradle must also be Java 25 or newer when the plugin is applied.

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

The release version in `gradle.properties` is `0.2.0`. Verify it before publishing;
`0.1.0` is already published and cannot be replaced.

Before publishing MyStem4j artifacts to a public repository, make sure every
transitive runtime dependency, including `io.github.ulviar:procwright:0.1.0`, is already
available from a repository that normal consumers can resolve.

The verified namespace `io.github.ulviar` covers the published group
`io.github.ulviar.mystem4j`, including the Gradle plugin marker. Complete the
[release checklist](release-checklist.md) before uploading.

## Configure Central credentials and signing once

Generate a username/password pair on the Central Portal
[User Tokens page](https://central.sonatype.com/usertoken). These are Portal token
credentials, not a GitHub password or personal access token.

Create or open your user-level Gradle properties file:

```bash
mkdir -p ~/.gradle
touch ~/.gradle/gradle.properties
chmod 600 ~/.gradle/gradle.properties
nano ~/.gradle/gradle.properties
```

Add the following entries in the editor, preserving existing settings:

```properties
mavenCentralUsername=YOUR_PORTAL_TOKEN_USERNAME
mavenCentralPassword=YOUR_PORTAL_TOKEN_PASSWORD
signing.gnupg.executable=gpg
signing.gnupg.keyName=YOUR_FULL_FINGERPRINT
```

Enter the actual token only in the editor, not in chat, a shell command, or the
repository's `gradle.properties`. This file stores the token as plaintext; mode
`600` restricts access to your user. Keep it outside the repository and do not
attach it to logs or issue reports.

The signing configuration uses Gradle's `useGpgCmd()` with your existing GnuPG
keyring. Use the full fingerprint of a secret key available on this machine.
The public key must also be accessible to Central through a supported keyserver.
The passphrase is handled by `gpg-agent`; no secret-key export, `secring.gpg`, or
passphrase property is needed. If `gpg` is not on Gradle's `PATH`, set
`signing.gnupg.executable` to its absolute path. See
[Gradle signing with GnuPG](https://docs.gradle.org/current/userguide/signing_plugin.html#sec:using_gpg_agent).

## Check a signed publication locally

Central support is opt-in through `-Pmystem4j.centralPublishing=true`. It enables
the Vanniktech base publishing plugin and signs the existing library, Gradle
plugin, and plugin marker publications. Normal checks and local unsigned
publication do not need a signing key or Portal credentials.

Prepare signed artifacts using the public dependencies with:

```bash
./gradlew publishToReleaseDryRunRepository \
  -Pmystem4j.centralPublishing=true
```

This writes artifacts and detached `.asc` signatures to
`build/release-dry-run-repo`; it does not upload them to Central. Verify the
signatures with `gpg --verify path/to/artifact.asc path/to/artifact` and test a
separate consumer against the local publication repository and Maven Central,
without Maven Local or private dependency repositories.

## Upload and release when dependencies are public

The runtime uses the published `io.github.ulviar:procwright:0.1.0`. Verify a consumer
using only the local publication repository and Maven Central.

After the release gates pass, upload with:

```bash
./gradlew publishToMavenCentral -Pmystem4j.centralPublishing=true
```

**This command uploads to Central. It is not a local dry run.** The root build
sets `automaticRelease=false`: after validation, inspect the deployment in
[Central Portal](https://central.sonatype.com/publishing/deployments) and explicitly
choose **Publish**. Do not run this command when only local preparation is
authorized. See the
[publishing plugin's manual-release workflow](https://vanniktech.github.io/gradle-maven-publish-plugin/central/#uploading-with-manual-publishing).

## Native integration checks

Run real MyStem integration checks before release:

```bash
./gradlew realMystemTest -Dmystem4j.executable=/path/to/mystem
```

Run the exhaustive Unicode offset stress gate when changing Unicode preprocessing
or MyStem offset alignment:

```bash
./gradlew realMystemUnicodeStress -Dmystem4j.executable=/path/to/mystem
```
