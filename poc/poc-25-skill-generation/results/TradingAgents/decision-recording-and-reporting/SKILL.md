---
name: decision-recording-and-reporting
description: Records the final decision to the persistent log, generates role-based and consolidated reports with provenance headers, and manages snapshot cleanup. Use when finalizing a run, persisting decisions, or generating output documents for operators.
---

# Decision Recording and Reporting

This skill handles the finalization phase of a run: persisting the decision, generating reports, and cleaning up temporary state.

## Core Workflow

1. **Determine Decision Status**
   - Check if a rating is readable from the analysis output.
   - If readable: Set status to `'pending'`.
   - If not readable: Set status to `'REVIEW'`.
   - *Why*: Unreadable ratings require human intervention; marking them as 'pending' would imply automated processing is complete.

2. **Persist Decision to Log**
   - Identify the unique key: `instrument` + `date`.
   - Check the persistent decision log for an existing entry with this key.
   - If no entry exists: Insert the new record with the determined status.
   - If an entry exists: Do **not** create a duplicate. Update the existing entry if necessary, but ensure only one record per instrument+date combination remains.
   - *Why*: Duplicate entries corrupt downstream analytics and reporting integrity.

3. **Generate Reports**
   - Construct the **Provenance Header**:
     - Include: Run date, version, models used, providers, analysts, and data sources.
     - **Exclude**: Credentials, API keys, local file paths, or any sensitive system information.
   - Generate **Role-Based Documents**:
     - Create one document per role.
     - Group these documents by team.
   - Generate **Consolidated Report**:
     - Create a single document summarizing all decisions.
   - Generate **Web View**:
     - Create a single-page HTML view of the results.

4. **Operator Interaction**
   - Display the generated results to the operator.
   - Offer the option to save the report to a specified location.
   - Wait for operator confirmation before proceeding to cleanup.

5. **Cleanup (Conditional)**
   - Check if checkpointing is enabled for the run.
   - If enabled AND the report generation was successful: Clear the snapshot.
   - If checkpointing is disabled OR generation failed: Do **not** clear the snapshot.
   - *Why*: Snapshots are safety nets; they should only be removed when the final output is confirmed successful.

## Gotchas

- **Status Logic**: Never mark a decision as `'pending'` if the rating is unreadable. It must be `'REVIEW'`. This distinction triggers different downstream workflows.
- **Provenance Security**: The provenance header is for auditability, not debugging. Never include local paths (e.g., `/home/user/...`) or credentials. This prevents information leakage in shared reports.
- **Idempotency**: The decision log must enforce uniqueness on `instrument+date`. If you attempt to insert a duplicate, the system must handle it gracefully (update or ignore) rather than creating a second row.
- **Snapshot Safety**: Do not clear the snapshot until the operator has seen the results and the report generation is confirmed successful. If the operator cancels the save, the snapshot must remain for recovery.
- **Report Structure**: Role-based documents are grouped by team, not just by role. Ensure the grouping logic is applied correctly to maintain organizational context.
