# Agent Infrastructure Evaluations

These scenario evaluations test whether a fresh agent can select sufficient context,
avoid stale artifacts, and produce verifiable work. Start each scenario with only
root `AGENTS.md`, the repository, and the scenario statement.

## Runtime Limit Change

Scenario: change the default response byte limit without changing reusable framing.

Expected behavior:

- loads runtime scope, runtime reference, builder/client tests;
- does not preload Lucene or historical specifications;
- tests one-shot, reusable, pooled, and error mapping where the limit propagates;
- updates user documentation only if the default is documented.

Failure signals: edits only one process mode, quotes a historical baseline as
authority, or claims completion from a builder unit test alone.

## Unicode Offset Failure

Scenario: MyStem removes a newly observed code point inside the first of two equal
words and Lucene highlights the second word.

Expected behavior:

- loads model scope first, then tokenization and Lucene scopes because offsets cross
  those boundaries;
- obtains or records a real-MyStem observation;
- adds contextual duplicate, property, and Lucene replay tests;
- preserves nearest compatible half-open UTF-16 ranges.

Failure signals: adds a replacement table without real evidence, tests one isolated
character only, or normalizes text without an original-offset mapping.

## Entity Enrichment Change

Scenario: support a new URL form only in entity-aware tokenization.

Expected behavior:

- loads tokenization scope and entity tests;
- keeps conservative/search presets unchanged unless explicitly requested;
- tests adjacent punctuation, invalid candidates, source partition, forms, and
  Lucene index semantics if emitted terms change.

Failure signals: changes defaults, consumes adjacent text, or copies a general URL
specification into project instructions.

## Gradle Download Change

Scenario: add another supported archive layout.

Expected behavior:

- loads the plugin scope; loads build-logic scope only if shared conventions or
  repository task wiring change;
- preserves license opt-in, checksum, archive limits, cache atomicity, and lazy
  providers;
- runs plugin tests, sample smoke, and configuration-cache checks.

Failure signals: runtime network access, unverified remote download, or task
execution during configuration.

## Interrupted Cross-Module Work

Scenario: resume an offset change after context compaction.

Expected behavior:

- reads the active-work file and verifies its base commit against current Git state;
- reattaches user constraints, changed files, decisions, failures, and next action;
- reruns stale evidence before continuing;
- removes the active-work file after tests/docs/ADR promotion.

Failure signals: trusts the handoff over the current diff, repeats completed
exploration, or archives the completed plan as current documentation.

## Historical Conflict

Scenario: a historical spec says Java 21 is supported while current build and
user docs require Java 25.

Expected behavior: treats current build and user contract as authoritative and uses
history only to explain the old statement.

Failure signal: changes current code to satisfy the retired baseline.

## Audit Artifact Cleanup

Scenario: an audit produces 20 findings, 5 accepted and 15 rejected.

Expected behavior: keeps raw output under `build/agent`, promotes accepted findings
to tests/fixes/docs/ADR, records rejection rationale in the task, and does not commit
the raw report by default.

Failure signals: adds a dated root audit as a second backlog or leaves accepted
findings only in prose.

## Completion Claim

Scenario: all narrow tests pass after a cross-module API change.

Expected behavior: checks public signatures, JPMS, docs, API baselines, module tests,
and repository gates before declaring completion.

Failure signal: equates one green test command with the full done condition.

Run these evaluations after major changes to `AGENTS.md`, context routing, artifact
policy, or the structural validator. Convert observed failures into an instruction,
validator, or executable project test rather than expanding the eval narrative.
