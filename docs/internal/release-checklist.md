# Release Checklist

Use this checklist before publishing a MyStem4j release.

## Required

- Confirm `gradle.properties` has the release version.
- If dependencies changed, run `./gradlew :module:dependencies --write-locks -Pmystem4j.useMavenLocal=true` for every affected module, including dependent modules. Inspect reports for resolution failures and review the lockfiles; `check` alone does not resolve every configuration.
- Run `./gradlew check -Pmystem4j.useMavenLocal=true`.
- Run `./gradlew memorySmokeTest -Pmystem4j.useMavenLocal=true`.
- Run `./gradlew unicodeContextStressTest -Pmystem4j.useMavenLocal=true`.
- Run `./gradlew javadoc javadocJar jar generatePomFileForMavenJavaPublication :mystem4j-gradle-plugin:generatePomFileForPluginMavenPublication :mystem4j-gradle-plugin:generatePomFileForMystem4jPluginMarkerMavenPublication -Pmystem4j.useMavenLocal=true`.
- Run `./gradlew realMystemTest -Pmystem4j.useMavenLocal=true -Dmystem4j.executable=/path/to/mystem`.
- Run `./gradlew realMystemUnicodeStress -Pmystem4j.useMavenLocal=true -Dmystem4j.executable=/path/to/mystem`.
- Run `./gradlew realMystemPoolSoak -Pmystem4j.useMavenLocal=true -Dmystem4j.executable=/path/to/mystem` and retain its throughput and p95/p99 output with the release evidence.
- Run `./gradlew :mystem4j-benchmarks:jmhSmoke -Pmystem4j.useMavenLocal=true`.
- Run `./gradlew releaseCandidateCheck -Pmystem4j.useMavenLocal=true`.
- Review `config/api-baseline`.
- Confirm `docs/internal/agent-work/active` contains only its `README.md`; promote
  durable outcomes and delete completed work files before tagging the release.
- Confirm the native MyStem binary is not bundled into published artifacts.

## Recommended

- Run the quality gates with `--configuration-cache` twice and confirm cache reuse.
- Publish to a temporary local Maven repository and test a separate consumer project on classpath and module path.
- Profile the real MyStem pool soak with JFR or heap histograms when runtime or
  iCLI process management changed.
