---
name: evaluation-sweep-orchestration
description: Orchestrates the evaluation (backtest) mode by executing a grid of instruments and dates through the full firm workflow, handling resumption, failure isolation, settlement, and summary generation. Use this skill when initiating, resuming, or completing a backtest sweep.
---

# Evaluation Sweep Orchestration

This skill manages the end-to-end execution of an evaluation (backtest) sweep. It ensures that the full firm workflow (analysts, debate, trader, risk, PM) is executed for each cell in a specified grid, while maintaining strict isolation between cells and accurate logging.

## Core Workflow

1.  **Initialize Grid**
    *   Accept the input parameters: `instruments` (list), `start_date`, `end_date`, and `step` (frequency).
    *   Generate the list of dates from `start_date` to `end_date` inclusive, using the specified `step`.
    *   **Constraint:** Filter out any dates that are in the future relative to the current system time. Do not include future dates in the grid.
    *   Construct the `cell_list` as the cross product of `instruments` and valid `dates`.

2.  **Pre-Flight Check & Resumption**
    *   Load the existing `evaluation_log`.
    *   Iterate through the `cell_list`.
    *   For each cell, check if it already exists in the `evaluation_log` with a completed status.
    *   If a cell is found in the log, mark it as `skipped`.
    *   If a cell is not found, mark it as `pending`.

3.  **Execute Pending Cells**
    *   Iterate through all `pending` cells.
    *   For each cell:
        *   **Isolation Check:** Ensure no position or state is carried forward from the previous cell. Each cell must start with a clean slate (flat positions, no pending orders).
        *   **Run Firm Workflow:** Execute the full firm sequence for this specific instrument and date:
            1.  Analysts
            2.  Debate
            3.  Trader
            4.  Risk
            5.  PM
        *   **Record Outcome:**
            *   If successful, record the cell's results (decision, alpha, etc.) in the `evaluation_log`.
            *   If an error occurs, record the failure in the `evaluation_log` with the error details. **Do not stop the sweep.** Continue to the next cell.

4.  **Settlement Pass**
    *   After all cells in the `cell_list` have been processed (either executed or skipped), initiate the settlement phase.
    *   For each unique `instrument` in the grid:
        *   Identify all decisions made during the sweep that are now due for settlement based on their holding period or exit conditions.
        *   Execute the settlement logic for these due decisions.
        *   Update the `evaluation_log` with the final settled outcomes.

5.  **Generate Summary**
    *   Aggregate the results from the `evaluation_log` for the current sweep.
    *   Group results by `rating`.
    *   For each rating group, calculate:
        *   **Count:** Total number of cells.
        *   **Hit Rate:** Percentage of cells where the directional prediction was correct relative to the realized alpha.
        *   **Mean Alpha:** The average alpha generated across all cells in the group.
    *   Format the summary report.

6.  **Final Logging**
    *   Write the final summary and any remaining log updates to the **evaluation's own log**.
    *   **Constraint:** Never write evaluation results or summaries to the operator's log. Keep evaluation data strictly separated.

## Decision Points & Rules

*   **Future Dates:** If a generated date is > current date, exclude it from the grid immediately. Do not attempt to run it.
*   **Resumption:** If a cell is in the log, skip it. Do not re-run it, even if the previous run failed, unless explicitly instructed to retry failures (default behavior is to skip all logged cells).
*   **Failure Handling:** A failure in one cell (e.g., data missing, model error) must not halt the sweep. Log the error and proceed. This ensures the sweep completes even with partial data issues.
*   **State Isolation:** Before starting a new cell, explicitly reset all state variables related to positions, orders, and risk limits. This prevents "look-ahead bias" or contamination from previous cells.

## Escalation & Exceptions

*   **Systemic Failure:** If more than 50% of cells fail due to the same error (e.g., data feed down), stop the sweep and escalate to the operator. Do not continue processing the remaining cells.
*   **Settlement Errors:** If settlement fails for an instrument, log the error but continue settling other instruments. Do not block the summary generation.

## Gotchas

*   **A delivery is not done until the recipient confirms:** In the context of settlement, a decision is not considered "settled" until the settlement pass explicitly processes it. Do not assume a trade is closed just because the PM made a decision; it must go through the settlement pass.
*   **Independence is critical:** The most common error in backtesting is state leakage. Ensure that the `Trader` and `Risk` modules do not retain memory of positions from the previous cell. If the firm workflow uses global state, you must manually reset it between cells.
*   **Log Separation:** Writing to the operator's log is a critical violation. Evaluation data is for analysis, not operational monitoring. Mixing them can corrupt operational dashboards.
*   **Hit Rate Definition:** Hit rate is directional correctness vs. alpha. A positive alpha with a wrong direction (e.g., predicted up, went down, but alpha was positive due to other factors) is a miss. Ensure the calculation logic aligns with this definition.
*   **Settlement Timing:** Settlement happens *after* all cells are processed, not during. This ensures that all decisions for an instrument are available for batch settlement, which may be more efficient and accurate than per-cell settlement.
