# ADR 0001: Agent Context And Artifact Lifecycle

Status: accepted
Scope: repository-wide LLM development workflow

## Context

MyStem4j development is performed primarily by LLM agents. Long conversations,
large audits, duplicated plans, and unmarked historical specifications can consume
context while providing contradictory guidance. Chat history and model compaction
are not sufficiently durable or precise to serve as the project system of record.

At the same time, a single enormous instruction file would be expensive to load,
difficult to maintain, and likely to hide local module constraints.

## Decision

The repository uses progressive context disclosure:

- root `AGENTS.md` is a compact map and operating contract;
- scoped module `AGENTS.md` files own local invariants;
- a context map routes task types to current sources and validation;
- accepted ADRs hold durable cross-cutting decisions;
- one active-work file may hold cross-session state for one objective;
- historical specifications are explicitly segregated and non-authoritative;
- raw audits, logs, context dumps, and exploratory output remain under ignored
  `build/agent` paths unless a user requests a durable report;
- completed active-work files are deleted after decisions and regressions are
  promoted to their owning artifacts;
- Gradle checks enforce instruction size, required structure, active-work shape,
  current-document reachability, and historical markings.

## Consequences

Agents must retrieve context by task instead of reading all internal documentation.
Cross-session work has a compact rehydration artifact, but routine work does not
create plan files. Git history replaces a completed-plan archive.

The system intentionally favors a few current owners over comprehensive historical
documentation. Historical rationale remains searchable but cannot override current
code, tests, contracts, or accepted decisions.

## Verification

- `agentInfrastructureCheck` passes and is attached to root `check`.
- Every module and `buildSrc` has a scoped instruction file within the configured
  size limit.
- Historical specs contain the required warning.
- Current internal documents are reachable from `docs/internal/README.md`; dynamic
  active-work files and archived details are validated by their own rules.
- Active-work files conform to the validated template and are absent after their
  objectives complete.
- Scenario evaluations in `docs/internal/agent/evals.md` remain solvable from the
  documented routing.
