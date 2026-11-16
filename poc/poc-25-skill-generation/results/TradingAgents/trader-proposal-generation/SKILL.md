---
name: trader-proposal-generation
description: Converts the Research Manager's investment plan into a concrete transaction proposal (Buy, Hold, Sell) with absolute price levels and position sizing for the Risk Team. Use this skill when finalizing trade execution details, determining entry/stop-loss prices, or handling malformed numeric data in trading instructions.
---

# Trader Proposal Generation

This skill defines the procedure for converting a Research Manager's investment plan into a finalized transaction proposal. The output must be a concrete, machine-readable proposal for the Risk Team, strictly adhering to absolute price formats and specific data hygiene rules.

## Core Workflow

1.  **Analyze the Investment Plan**: Review the Research Manager's plan and the associated technical report. Identify the recommended direction and the technical price structure (support/resistance levels, trend lines).
2.  **Determine the Action**: Select one of three actions based on the three-level transaction scale:
    *   **Buy**: Initiate or add to a long position.
    *   **Hold**: Maintain current position without new capital deployment.
    *   **Sell**: Reduce or close a long position.
3.  **Establish Price Levels**: Extract specific price points from the technical report. You must state:
    *   **Entry Price**: The exact price at which the trade is initiated.
    *   **Stop-Loss Price**: The exact price at which the position is exited to limit loss.
    *   *Constraint*: These must be **absolute prices** (e.g., `150.25`). Never use percentages (e.g., `5% below entry`) or ranges (e.g., `145-150`).
4.  **Determine Position Sizing**:
    *   **If Operator Holdings are Provided**: Calculate the size of the new transaction relative to the real, existing position. Ensure the proposal reflects the net change or total exposure accurately.
    *   **If Operator Holdings are NOT Provided**: Provide general guidance for the operator's own position sizing based on the plan's risk parameters. Do not assume a default position size.
5.  **Draft Reasoning**: Provide a concise narrative explaining the decision. Link the action and price levels directly to the technical report's price structure and the Research Manager's thesis.
6.  **Validate and Sanitize Data**: Before finalizing, run the numeric field validation protocol (see below).
7.  **Output Proposal**: Generate the final transaction proposal object for the Risk Team.

## Data Hygiene & Malformed Numeric Fields

Numeric fields (Entry, Stop-Loss, Size) are prone to formatting errors. Apply the following rules strictly to ensure the proposal is parsable:

1.  **Drop Placeholders and Percentages**: If a field contains text like `TBD`, `N/A`, `--`, or a percentage value (e.g., `2%`), discard the value. Do not attempt to convert percentages to prices unless the base price is explicitly defined in the same context.
2.  **Reduce Formatted Prices to Numbers**: Strip currency symbols (`$`, `€`), commas, and whitespace. Convert `"$1,234.56"` to `1234.56`.
3.  **Null Bad Fields, Do Not Fail**: If a specific numeric field is malformed and cannot be reliably converted to a number, set that specific field to `null`. **Do not** reject the entire transaction proposal. The Risk Team can handle null fields; they cannot handle a failed generation.

## Decision Rules

*   **Action Selection**: The action must align with the Research Manager's plan. If the plan is ambiguous, default to **Hold** and flag the ambiguity in the reasoning.
*   **Price Grounding**: Entry and Stop-Loss prices must be derived from the technical report's price structure. If the report suggests a range, select the specific price level that aligns with the risk/reward profile defined in the plan.
*   **Sizing Logic**:
    *   *With Holdings*: `New Size = Target Exposure - Current Exposure`. If the result is negative, the action is likely Sell/Reduce.
    *   *Without Holdings*: Output a recommended size unit (e.g., shares, contracts) based on the plan's risk budget, clearly labeled as "Guidance".

## Gotchas

*   **Absolute Prices Only**: A common failure is outputting `Stop-Loss: 5%`. This is invalid. You must calculate the absolute price (e.g., `Stop-Loss: 95.00`) based on the Entry Price. If the Entry Price is null, the Stop-Loss must also be null.
*   **Partial Data is Better Than No Data**: If the Entry Price is valid but the Stop-Loss is malformed, output the valid Entry Price and `null` for Stop-Loss. Do not discard the entire proposal because one field is bad.
*   **Holdings Context Matters**: If the operator provides holdings, the "Size" field represents the *delta* (change) or the *total* depending on the system's convention. Always clarify in the reasoning whether the size is incremental or total. If unclear, assume incremental and state this assumption.
*   **Technical Report Dependency**: Do not invent price levels. If the technical report lacks specific price structure data, set Entry/Stop-Loss to `null` and note the missing data in the reasoning. Do not guess prices.
*   **Three-Level Scale**: Do not use intermediate actions like "Buy More" or "Partial Sell". Map these to the three-level scale: "Buy More" -> **Buy**, "Partial Sell" -> **Sell** (with size reflecting the partial nature).
