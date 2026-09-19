# Build Logic Agent Scope

## Scope

`buildSrc` owns reusable conventions and repository verification tasks. Changes can
affect every module even when production APIs are untouched.

## Read

- The task or convention class being changed
- Root task registration in `build.gradle.kts`
- [Release checklist](../docs/internal/release-checklist.md)
- Existing configuration-cache and publication checks

## Invariants

- Task inputs and outputs are declared and configuration-cache compatible.
- Task actions use properties captured during configuration, not `Project` access.
- Paths are normalized and diagnostics are deterministic and actionable for an
  agent.
- Verification tasks fail closed and explain the smallest safe remediation.
- Build helpers remain Java 25 and cross-platform.
- A new invariant must be attached to `check` or the narrow owning gate and listed
  in the testing strategy.

## Validation

```text
./gradlew agentInfrastructureCheck check --configuration-cache -Pmystem4j.useMavenLocal=true
./gradlew check --configuration-cache -Pmystem4j.useMavenLocal=true
```

Run the command twice and confirm configuration-cache reuse.
