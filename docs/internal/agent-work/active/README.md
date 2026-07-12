# Active Cross-Session Work

This directory contains only currently active tasks that need to survive context
compaction, session boundaries, or an agent handoff.

Create a file from the [active-work template](../../agent/templates/active-work.md).
The structural validator checks every file here except this README.

Do not use this directory for ordinary plans, completed work, audits, logs, or
backlogs. Delete an active file after promoting its durable decisions, regressions,
and documentation. Git history is the completed-work archive.
