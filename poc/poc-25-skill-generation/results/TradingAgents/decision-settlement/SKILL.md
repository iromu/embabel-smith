---
name: decision-settlement
description: Settles pending trading decisions by calculating returns and alpha against regional benchmarks after the holding window expires, generating AI reflections, and updating the decision log. Use this skill when processing the learning loop for past decisions, specifically when identifying decisions whose holding window (default 5 trading days) has fully traded since the analysis date.
---

# Decision Settlement

This skill manages the end-to-end settlement of pending trading decisions. It ensures that past decisions are evaluated against market data, reflected upon by an AI model, and recorded in the decision log with appropriate status updates.

## Core Workflow

1.  **Identify Pending Decisions**
    *   Query the decision log for entries with status `pending`.
    *   Filter for decisions where the holding window (default: 5 trading days) has fully elapsed since the `analysis_date`.
    *   *Note:* A decision is only eligible for settlement if the current date is strictly after the last trading day of the holding window.

2.  **Resolve Benchmark**
    *   Determine the instrument's market.
    *   Resolve the correct regional benchmark index for that market.
    *   *Why:* Alpha calculation is meaningless without the correct market-relative benchmark.

3.  **Fetch Price Data**
    *   Retrieve the instrument's closing prices starting from the **first close after the analysis date**.
    *   Retrieve the benchmark's closing prices for the same period.
    *   Ensure data covers the entire holding window.

4.  **Compute Metrics**
    *   Calculate the **Raw Return** of the instrument over the holding window.
    *   Calculate the **Alpha** (instrument return minus benchmark return) over the same window.
    *   Record the `known_by_date` as the date the final closing price of the window was available.

5.  **Generate Reflection**
    *   Prompt the AI model to generate a 2-4 sentence reflection on the outcome.
    *   The reflection must note if the holding window was too short to definitively judge the original thesis.
    *   *Why:* Short windows often produce noise; the reflection must contextualize the result rather than treating it as absolute truth.

6.  **Update Decision Log**
    *   Update the decision entry status from `pending` to `settled`.
    *   Persist the following fields:
        *   `return` (raw return)
        *   `alpha`
        *   `window` (duration used)
        *   `known_by_date`
        *   `reflection` (AI-generated text)

## Failure Handling

*   **Missing Data:** If price data for the instrument or benchmark is unavailable, **do not** update the status. Leave the decision as `pending`.
*   **Reflection Failure:** If the AI model fails to generate a reflection, **do not** update the status. Leave the decision as `pending`.
*   **Non-Blocking:** Failures must not block the current run. Log the failure reason and proceed to the next decision.

## Concurrency & Idempotency

*   **One Pass at a Time:** Ensure only one settlement process runs per decision log at any given time to prevent race conditions.
*   **Idempotency:** Re-running the settlement for an already `settled` decision must not alter the existing record or cause errors. Check status before processing.

## Gotchas

*   **First Close After Analysis:** Always use the first close *after* the analysis date as the start of the return calculation. Using the analysis date's close itself is incorrect as it may not reflect the entry point.
*   **Window Expiry:** Do not settle if the holding window has not fully traded. If the market was closed for holidays within the window, ensure the "fully traded" condition accounts for actual trading days, not just calendar days.
*   **Short Window Warning:** The AI reflection *must* explicitly mention if the window is too short to judge the thesis. Do not suppress this warning even if the return is positive.
*   **Status Integrity:** Never mark a decision as `settled` if any component (return, alpha, reflection) is missing or null. Partial settlement corrupts the learning loop.
*   **Benchmark Accuracy:** Using a global benchmark for a regional instrument (or vice versa) invalidates the alpha calculation. Always resolve the benchmark based on the instrument's specific market.
