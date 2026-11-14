---
name: market-technical-analysis
description: Generates detailed technical market analysis reports by retrieving verified OHLCV data and technical indicators from configured providers, enforcing point-in-time integrity, and handling data source failures via fallback chains. Use this skill when asked to perform technical analysis, generate price/indicator reports, or retrieve historical market data for a specific date.
---

# Market/Technical Analysis Skill

This skill governs the retrieval of verified price data (OHLCV) and technical indicators to produce grounded technical reports. It enforces strict data integrity, point-in-time accuracy, and explicit failure handling.

## Core Workflow

1.  **Define Analysis Scope**
    *   Identify the asset(s) and the specific **analysis date** required.
    *   Determine the required technical indicators: Trend, Momentum, Volatility, and Volume.

2.  **Enforce Point-in-Time Integrity**
    *   **Clamp all data requests** to the specified analysis date.
    *   Do not request future data or data beyond the analysis date.
    *   Ensure that any indicator calculated uses only data available up to and including the analysis date.

3.  **Retrieve Data via Fallback Chain**
    *   Attempt to retrieve OHLCV and indicator data from the **primary configured data provider**.
    *   **If successful:** Proceed to validation.
    *   **If throttled (rate-limited):** Immediately try the **next provider** in the configured fallback chain.
    *   **If no data returned:** Try the next provider in the fallback chain.
    *   **If all providers fail:** Proceed to Step 5 (Explicit Failure Reporting).

4.  **Validate and Ground Claims**
    *   Verify that the retrieved data forms a consistent **verified price snapshot**.
    *   Cross-check that all price and indicator claims in the report are directly grounded in this snapshot.
    *   **Flag discrepancies:** If retrieved data conflicts with known market context or internal consistency checks, flag the discrepancy explicitly. Do not invent reconciled numbers.

5.  **Generate Report**
    *   Produce a detailed technical report based **only** on the verified snapshot.
    *   Include OHLCV data and the curated set of technical indicators (Trend, Momentum, Volatility, Volume).
    *   If data was unavailable from all sources, report the status as **'unavailable'** for the affected fields. Do not estimate or fabricate values.

## Decision Points & Rules

### Data Source Failure Handling
*   **Rule:** If a source is throttled, skip it and try the next in the fallback chain.
*   **Rule:** If a source returns no data, skip it and try the next in the fallback chain.
*   **Rule:** If **all** configured sources return no data, report the data as **'unavailable'**.
*   **Why:** Estimating or fabricating values introduces hallucination risk and breaks auditability. Explicit 'unavailable' status allows downstream processes to handle missing data correctly.

### Point-in-Time Integrity
*   **Rule:** All data requests must be clamped to the analysis date.
*   **Why:** Using future data (look-ahead bias) invalidates technical analysis and creates misleading reports. The snapshot must reflect what was known at the analysis date.

### Grounding and Discrepancies
*   **Rule:** All exact price/indicator claims must be grounded in the verified snapshot.
*   **Rule:** If a discrepancy is detected between sources or within the data, flag it. Do not average or reconcile conflicting numbers silently.
*   **Why:** Silent reconciliation hides data quality issues. Flagging ensures transparency and allows for human review or alternative data sourcing.

## Gotchas

*   **Throttling is not failure:** A throttled response (e.g., HTTP 429) is not a data failure. It is a signal to switch providers. Do not report 'unavailable' if a fallback provider succeeds.
*   **'Unavailable' is a valid state:** Never estimate OHLCV or indicator values if all sources fail. Reporting 'unavailable' is the correct and required behavior.
*   **No look-ahead bias:** Ensure that indicators like Moving Averages or RSI are calculated using only data up to the analysis date. Do not use data from after the analysis date, even if available in the provider's current dataset.
*   **Discrepancies are not errors to fix:** If two sources disagree on a price, do not pick one or average them. Flag the discrepancy. The goal is to report the verified snapshot, not to create a 'best guess'.
*   **Curated indicators only:** Use only the configured set of technical indicators (Trend, Momentum, Volatility, Volume). Do not invent new indicators or use unverified calculations.
