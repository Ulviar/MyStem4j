# Architecture Decision Records

Decision records capture durable choices that are difficult to infer from code and
likely to constrain future work. They do not duplicate implementation plans, test
results, or user documentation.

Create an ADR when a decision:

- crosses module boundaries;
- rejects a plausible alternative that future agents may reintroduce;
- defines ownership, compatibility, security, or artifact lifecycle;
- cannot be enforced or explained sufficiently by one focused test or comment.

Use sequential names such as `0002-short-title.md` with these sections:

```text
# ADR NNNN: Title
Status: proposed | accepted | superseded by ADR NNNN
Scope: affected modules or workflow

## Context
## Decision
## Consequences
## Verification
```

An accepted ADR is authoritative for its decision until explicitly superseded. Do
not edit old rationale to pretend a later decision was always true; add a new ADR
and link both records.

Records:

- [ADR 0001: Agent context and artifact lifecycle](0001-agent-context-and-artifact-lifecycle.md)
- [ADR 0002: Builder-only configuration APIs](0002-builder-only-configuration-apis.md)
- [ADR 0003: Executable module boundaries](0003-executable-module-boundaries.md)
