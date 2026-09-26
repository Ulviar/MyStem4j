# Validate a local change

This is the working loop for the independent, unreleased project. A local change
is complete when its contract, tests and documentation agree; publishing is not a
prerequisite.

## Start with the affected contract

Use the [context map](agent/context-map.md) to find the owning module and its
narrow test command. Reproduce a behavior defect before changing its implementation.
For module boundaries, offsets or process lifetime, retain an adversarial test
that would fail with the original behavior. Run the owning module test after the
focused test passes.

Install JDK 25 and use the repository's `./gradlew` wrapper. The checked-in daemon
criteria and compilation toolchains select Java 25 for Gradle, compilation and
tests. They use an installed JDK; the build does not download a JDK automatically.
Procwright `0.1.0` and the other library dependencies resolve from Maven Central.
No Maven Local installation or private repository credentials are needed.
Do not update lockfiles or API baselines just to remove a failure.

## Check the complete checkout

```bash
./gradlew agentInfrastructureCheck check --configuration-cache
./gradlew agentInfrastructureCheck check --configuration-cache
```

The second invocation should report configuration-cache reuse. `check` owns module
tests, Java/Kotlin API baselines, classpath and JPMS consumers, dependency boundaries,
publication metadata, coverage floors and documentation checks. Build-logic tests
run before these gates can use the helpers.

## Exercise the affected heavy paths

| Change | Additional gate |
| --- | --- |
| Retention, buffers or lifecycle | `memorySmokeTest` |
| Unicode preparation, alignment or token boundaries | `unicodeContextStressTest realMystemUnicodeStress` |
| Protocol framing, process failure or lifecycle | `realMystemTest realMystemPoolSoak` |
| Gradle preparation or artifact wiring | `sampleSmokeTest` |
| Performance or benchmark fixtures | [performance measurement procedure](../how-to/measure-performance.md) |

Native tasks require an explicit working executable. They do not download one:

```bash
./gradlew memorySmokeTest unicodeContextStressTest realMystemTest \
  realMystemUnicodeStress realMystemPoolSoak \
  -Dmystem4j.executable=/absolute/path/to/mystem
```

On an Apple Silicon machine using the x64 macOS distribution, first confirm the
binary runs through the installed Rosetta. Do not treat a skipped native test as
validation. Inspect the task log and reports under each module's `build/reports`.

## Finish

Review the diff for unrelated edits, update the owning contract when
behavior changes, and retain command logs under `build/agent/logs`. Promote any
active-work decisions to source, tests or current docs and delete the completed
active-work file. The [testing strategy](testing-strategy.md) owns the detailed
invariants; this page only selects the commands needed for a local change.
