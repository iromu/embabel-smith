---
name: run-configuration-and-validation
description: Configures and validates the setup phase for a single-instrument analysis run. Use this skill when initializing a new analysis task or resuming an interrupted one, ensuring all parameters (ticker, date, analysts, models) are valid, credentials are present, and run identity matches any existing snapshot.
---

# Run Configuration and Validation

This skill governs the pre-execution setup for a single-instrument analysis. It ensures that all inputs are safe, complete, and consistent before the analysis engine starts.

## Core Workflow

1.  **Gather Parameters**: Collect all required run parameters:
    *   Instrument ticker
    *   Analysis date
    *   Selected analyst subset
    *   Research depth
    *   AI model/provider choices (for both 'quick' and 'deep' tiers)
    *   Output language
    *   Optional: Holdings snapshot
    *   Optional: Resume-from-interruption flag

2.  **Apply Defaults**: If available, offer answers from the previous run as defaults for any missing optional parameters.

3.  **Validate Inputs**: Perform strict validation on all gathered parameters (see *Validation Rules* below).

4.  **Check Credentials**: Perform pre-flight credential checks for the AI providers actually selected in the configuration.

5.  **Handle Resume Logic** (if `resume-from-interruption` is true):
    *   Compute the **Run Identity Signature** based on: instrument, date, analysts, depth, asset type, holdings, models, and settings.
    *   Check for a saved snapshot matching this signature.
    *   If a snapshot exists and matches, proceed with resume.
    *   If the configuration differs from the snapshot, **discard the resume intent** and start a fresh run.

6.  **Output Configuration**: Return a validated, complete run configuration object ready for execution.

## Validation Rules

Apply these checks sequentially. If any check fails, halt the process and report the specific error.

*   **Date Validation**: Reject any analysis date that is in the future.
*   **Ticker Safety**: Reject malformed or unsafe tickers. Specifically, reject any ticker containing path-traversal style characters (e.g., `../`, `..\`, null bytes).
*   **Analyst Selection**: Ensure at least one analyst is selected. Reject the run if the analyst subset is empty.
*   **Crypto Constraint**: If the instrument is a cryptocurrency, ensure the **Fundamentals Analyst** is **not** selected. Reject the run if this combination is detected.

## Credential Handling

*   Identify which AI providers are active based on the selected models for 'quick' and 'deep' tiers.
*   Verify that credentials for these specific providers are present.
*   **Missing Credentials Protocol**:
    *   If a credential is missing **and** no terminal is available for interactive input:
        1.  Stop the run immediately before execution begins.
        2.  List **every** missing parameter (including the missing credential and any other validation failures found so far).
        3.  Do not proceed to output a configuration object.

## Resume Logic Details

*   **Run Identity Signature**: A hash or unique identifier derived from the tuple: `(instrument, date, analysts, depth, asset type, holdings, models, settings)`.
*   **Mismatch Handling**: If the user requests a resume but the current configuration's signature does not match the saved snapshot's signature, the system must **start fresh**. Do not attempt to merge or partially resume. This prevents state corruption from mismatched contexts.

## Gotchas

*   **Future Dates are Hard Failures**: Do not attempt to adjust the date to 'today' if a future date is provided. Reject it explicitly.
*   **Crypto vs. Fundamentals**: The Fundamentals Analyst is incompatible with crypto instruments. This is a hard constraint, not a warning. If a user selects both, the run is invalid.
*   **Resume is Conditional**: Enabling the resume flag does not guarantee a resume. If the configuration has changed (even slightly, e.g., a different model or added holding), the system must silently revert to a fresh start to maintain data integrity.
*   **Credential Scope**: Only check credentials for providers actually in use. Do not fail the run for missing credentials for unused providers.
*   **Terminal Dependency**: The behavior for missing credentials depends on the environment. If no terminal is available, you cannot prompt the user. You must fail fast and list all missing items at once to minimize round-trips.
*   **Path Traversal**: Ticker validation is a security boundary. Treat any ticker resembling a file path or containing traversal sequences as malicious and reject it without further processing.
