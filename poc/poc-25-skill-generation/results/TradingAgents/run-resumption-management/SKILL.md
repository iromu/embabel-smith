---
name: run-resumption-management
description: Manages the checkpointing and resumption logic for interrupted runs. Use this skill when initiating a run to determine whether to resume from a saved snapshot or start fresh, and when finalizing a run to clear or retain snapshots based on success or failure.
---

# Run Resumption Management

This skill governs the reliability mechanism for interrupted runs. It ensures that work is not lost during failures and that runs do not duplicate completed steps when resumed.

## Core Workflow

### 1. Initialization & Identity Computation
When a new run invocation begins:
1.  **Check Enablement**: Verify if `resume-from-interruption` is enabled in the configuration. If disabled, skip all checkpointing logic and proceed as a standard fresh run.
2.  **Compute Identity**: Calculate the **run identity signature**. This signature must uniquely identify the specific run instance based on its core parameters (e.g., input data hash, configuration version, unique run ID).
3.  **Snapshot Lookup**: Check if a saved snapshot exists for this exact identity signature.

### 2. Decision Logic: Resume vs. Fresh Start
Evaluate the existence and validity of the snapshot:

*   **Case A: No Snapshot Exists**
    *   Action: Start the run from the beginning (Step 1).
    *   Report: `started_fresh`

*   **Case B: Snapshot Exists & Configuration Matches**
    *   Action: Resume execution from the **last completed step** recorded in the snapshot.
    *   Report: `resumed`

*   **Case C: Snapshot Exists & Configuration Differs**
    *   Action: **Ignore** the existing snapshot. Start the run from the beginning (Step 1).
    *   Reasoning: A configuration change implies the previous state is invalid or incompatible. Resuming could lead to inconsistent results.
    *   Report: `started_fresh` (Note: The old snapshot is effectively orphaned; it may be cleaned up by a separate garbage collection process, but this skill does not delete it immediately upon mismatch).

### 3. Execution & Checkpointing
During the run execution:
1.  **Step Completion**: After each individual step completes successfully, save a new snapshot of the current run state.
2.  **Snapshot Content**: The snapshot must include:
    *   The run identity signature.
    *   The index/ID of the last completed step.
    *   Any intermediate state required to restart from that point.

### 4. Finalization
Upon run termination, handle the snapshot based on the outcome:

*   **Success**
    *   Action: **Clear** (delete) the snapshot associated with this run identity.
    *   Reasoning: The run is complete; retaining the snapshot wastes storage and prevents future re-runs from accidentally resuming a completed job.

*   **Failure (Mid-stream)**
    *   Action: **Keep** the snapshot.
    *   Reasoning: The run failed before completion. The snapshot allows a future invocation to resume from the last successful step rather than restarting from zero.

## Reporting
Always return a status indicator indicating the mode of operation:
*   `resumed`: The run continued from a previous checkpoint.
*   `started_fresh`: The run began from the beginning (either no snapshot existed, or the snapshot was invalid due to config mismatch).

## Gotchas

*   **Identity Signature Precision**: The run identity signature must be deterministic. If the signature changes due to non-deterministic factors (e.g., timestamps, random seeds not fixed in config), valid snapshots will be ignored, causing unnecessary restarts. Ensure the signature only includes stable, defining parameters of the run.
*   **Configuration Mismatch is Fatal for Resumption**: Do not attempt to "patch" a snapshot when the configuration differs. Even minor config changes can invalidate intermediate states. Always start fresh if the config hash differs from the snapshot's stored config hash.
*   **Snapshot Cleanup on Success**: Failing to clear the snapshot on success is a critical bug. It causes subsequent runs with the same identity to incorrectly resume from a completed state, potentially skipping necessary re-execution or causing logic errors.
*   **Partial Step Failures**: Checkpoints are saved *after* a step completes. If a step fails midway, the snapshot still points to the *previous* completed step. This is intentional to ensure idempotency; the failed step will be re-executed on resume.
*   **Disabled Resumption**: If `resume-from-interruption` is disabled, do not read or write snapshots. This ensures zero overhead for runs where reliability via checkpointing is not required.
