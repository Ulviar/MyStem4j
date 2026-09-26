# Release Checklist

Use this checklist before publishing a MyStem4j release.

The process dependency is `io.github.ulviar:procwright:0.1.0`, available from Maven
Central. Run the release gates with the default repository configuration.

## Required

- Confirm `gradle.properties` has the release version.
- If dependencies changed, run `./gradlew :module:dependencies --write-locks` for every affected module, including dependent modules. Inspect reports for resolution failures and review the lockfiles; `check` alone does not resolve every configuration.
- Run `./gradlew check`.
- Run `./gradlew memorySmokeTest`.
- Run `./gradlew unicodeContextStressTest`.
- Run `./gradlew javadoc javadocJar jar generatePomFileForMavenJavaPublication :mystem4j-gradle-plugin:generatePomFileForPluginMavenPublication :mystem4j-gradle-plugin:generatePomFileForMystem4jPluginMarkerMavenPublication`.
- Run `./gradlew realMystemTest -Dmystem4j.executable=/path/to/mystem`.
- Run `./gradlew realMystemUnicodeStress -Dmystem4j.executable=/path/to/mystem`.
- Run `./gradlew realMystemPoolSoak -Dmystem4j.executable=/path/to/mystem` and retain its throughput and p95/p99 output with the release evidence.
- Run `./gradlew :mystem4j-benchmarks:jmhSmoke`.
- Run `./gradlew releaseCandidateCheck`.
- Review `config/api-baseline`.
- Confirm `docs/internal/agent-work/active` contains only its `README.md`; promote
  durable outcomes and delete completed work files before tagging the release.
- Confirm the native MyStem binary is not bundled into published artifacts.

## Before a Central upload

- Configure the Portal token and local GnuPG signing as described in
  [publication notes](publication.md#configure-central-credentials-and-signing-once).
- Confirm all published runtime dependencies resolve from Maven Central without
  Maven Local or a private repository.
- Run `./gradlew publishToReleaseDryRunRepository -Pmystem4j.centralPublishing=true`.
  Inspect the artifact set, including the Gradle plugin marker, and verify the
  detached signatures with GnuPG.
- Test a separate consumer against the local publication repository and Maven
  Central, with no Maven Local or private repository fallback.
- Only when upload is authorized, run
  `./gradlew publishToMavenCentral -Pmystem4j.centralPublishing=true`. This sends
  artifacts to Central even though automatic release is disabled. Review the
  validated deployment and explicitly choose **Publish** in Central Portal.

## Recommended

- Run the quality gates with `--configuration-cache` twice and confirm cache reuse.
- Exercise the separate consumer on both classpath and module path.
- Profile the real MyStem pool soak with JFR or heap histograms when runtime or
  Procwright process management changed.
