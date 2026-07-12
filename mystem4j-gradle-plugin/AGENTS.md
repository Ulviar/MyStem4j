# Gradle Plugin Agent Scope

## Scope

This module prepares a native MyStem executable and exposes task-backed providers
for tests and application distributions.

## Read

- [Plugin reference](../docs/reference/gradle-plugin.md)
- [Preparation guide](../docs/how-to/prepare-mystem-with-gradle.md)
- Plugin functional and task tests under `src/test`
- [Binary boundary](../docs/explanation/architecture.md#mystem-binary-boundary)

## Invariants

- Downloads require explicit opt-in and explicit Yandex license acceptance.
- Remote archives require a trusted SHA-256, bounded size, retry policy, and atomic
  cache publication.
- Extraction and executable selection must remain platform aware and path safe.
- Public providers are lazy and task backed; custom `Copy`/`Sync` usage must not
  force probing unexpectedly.
- Build logic must support configuration cache and avoid execution-time project
  model access.
- The plugin prepares files but does not decide how users package installers,
  containers, or Kubernetes workloads.

## Validation

```text
./gradlew :mystem4j-gradle-plugin:test -Pmystem4j.useMavenLocal=true
./gradlew sampleSmokeTest -Pmystem4j.useMavenLocal=true
```
