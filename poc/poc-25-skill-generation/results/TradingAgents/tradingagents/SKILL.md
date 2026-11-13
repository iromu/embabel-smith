---
name: tradingagents
description: Execute a single-instrument investment analysis by simulating a multi-agent trading firm, or settle past decisions to generate learning lessons. Use when analyzing a specific ticker on a given date, running the full analyst-researcher-trader-risk-manager pipeline, or processing pending decision logs.
---

# TradingAgents Operating Procedure

## 1. Core Workflow: Single-Instrument Analysis

Follow these steps strictly in order. Do not skip pre-flight checks.

### Step 1: Configuration & Pre-Flight
1. **Gather Parameters:** Obtain the instrument (ticker), analysis date, selected analysts (non-empty subset), research depth, AI models (quick/deep tiers), output language, and optional holdings snapshot.
2. **Validate Date:** Reject any future analysis date immediately.
3. **Validate Instrument:** Ensure the ticker is valid and safe (no path traversal). If invalid, stop.
4. **Check Credentials:** Verify API keys/endpoints for all configured AI models and data sources. If missing, stop and list exactly what is required. Do not start the run.
5. **Resolve Identity:** Deterministically resolve the instrument's real name and classification. Inject this into all prompts to prevent role confusion. If lookup fails, proceed with ticker-only context (fail-open).

### Step 2: Parallel Analyst Execution
Start the selected analysts simultaneously. Each works from its own evidence slice:
*   **Market/Technical:** Uses verified price snapshots. Must ground claims in exact data.
*   **Sentiment:** Uses news/social feeds. *Note:* These are not archived; historical runs have point-in-time limitations here.
*   **News/Macro:** Uses company/global news and macro indicators. Macro/prediction market failures degrade gracefully (report as unavailable, do not abort).
*   **Fundamentals:** Uses financial statements. For US filers, use SEC filings "as filed" with filing dates. For non-US or crypto, adjust accordingly (Crypto has no Fundamentals analyst).

**Concurrent Task:** While analysts run, settle any due past decisions (see Section 2) and load relevant lessons from the decision log for this instrument.

### Step 3: Research Debate
*   **Gate:** Wait until **every** selected analyst has filed a report. If an analyst is absent, mark it explicitly as "No report available," not "neutral."
*   **Debate:** Bull and Bear researchers take turns rebutting each other. One round = one Bull turn + one Bear turn. Continue until the configured depth is reached.
*   **Verdict:** The Research Manager weighs both sides and issues a structured investment plan with a **five-level recommendation** (Buy/Overweight/Hold/Underweight/Sell).

### Step 4: Trader Proposal
*   Convert the Research Manager's plan into a concrete transaction.
*   Output: Action (**Buy/Hold/Sell**), reasoning, entry price, stop-loss, and position sizing.
*   **Constraint:** Entry/stop must be absolute prices, never percentages. If the operator provided holdings, size against them; otherwise, provide general guidance.

### Step 5: Risk Debate
*   Three risk analysts (Aggressive, Conservative, Neutral) debate the Trader's proposal in fixed order.
*   One round = one turn each. Continue until the configured depth is reached.

### Step 6: Portfolio Manager Decision
*   The Portfolio Manager reviews the Research Plan, Trader Proposal, Risk Debate, and loaded lessons.
*   Issue the final **five-level rating**, executive summary, and investment thesis.
*   **Critical Rule:** If the final decision does not contain a readable five-level rating, record the run as **REVIEW**. Do **not** default to Hold.

### Step 7: Recording & Output
1.  Append the decision to the persistent decision log as **Pending**.
2.  Generate the report (documents per role + consolidated web view).
3.  Display results to the operator.

---

## 2. Settlement Workflow (Learning Loop)

Triggered automatically during Step 2 of a new run, or on demand.

1.  **Identify Due Decisions:** Find pending decisions where the holding window (default 5 trading days from the first close after analysis date) has fully traded.
2.  **Fetch Data:** Retrieve closing prices for the instrument over the holding window.
    *   *Exception:* If the market was closed for part of the window, extend the request to today to check if the window can be completed. If still incomplete, leave pending.
3.  **Calculate Returns:** Compute raw return and alpha relative to the regional benchmark (auto-selected by market, e.g., S&P 500 for US).
4.  **Generate Reflection:** Ask the AI model for a 2–4 sentence reflection on what the alpha indicates about the thesis.
5.  **Update Log:**
    *   If successful: Update the entry from **Pending** to **Settled**, adding return, alpha, known-by date, and reflection.
    *   If failed (prices/reflection unavailable): Leave as **Pending** and report as unsettled. Do not block the main run.

---

## 3. Evaluation Mode (Grid Scoring)

Used to score decision quality over multiple instruments/dates. Not a portfolio simulator.

1.  **Build Grid:** Cross-product of tickers and dates (step interval). Never include future dates.
2.  **Run Cells:** Execute the full firm for each cell.
    *   Cells are independent; no positions carry forward.
    *   If a cell fails, record it and continue the sweep.
    *   If resuming a previous sweep ID, skip cells already in the evaluation log.
3.  **Settle:** After the grid completes, run settlement for each instrument's last cell.
4.  **Summarize:** Group by rating. Calculate hit rate (direction match vs. realized alpha) and mean alpha. Note that Hold ratings claim no direction and are unscored for directional correctness.

---

## 4. Resume Workflow

If resume-from-interruption is enabled:
1.  **Compute Signature:** Hash the instrument, date, analysts, depth, asset type, holdings, and models.
2.  **Check Snapshot:**
    *   If a saved snapshot exists for this exact signature, resume from the last completed step.
    *   If the configuration differs from the snapshot, **start fresh**. Do not silently continue a different run.
3.  **Completion:** On success, clear the snapshot. On failure, keep the snapshot for later retry.

---

## 5. Decision Points & Branching Rules

| Situation | Action |
| :--- | :--- |
| **Analyst Report Missing** | Mark as "Absent/Not Available." Do not treat as neutral or empty finding. Downstream roles must know the data is missing. |
| **Data Source Failure** | Try next source in configured fallback chain. If all fail, report "No data available" and instruct analyst to state unavailability. Do not fabricate values. |
| **Macro/Prediction Market Failure** | Degrade gracefully. Report as unavailable. Proceed with run. |
| **Core Data (Price/Fundamental) Failure** | Surface loudly. If critical data is missing, the run may need to stop or report significant gaps. |
| **Past-Dated Run Needs Live Data** | Withhold live-only data (e.g., current sector, market cap). State that serving it would leak post-decision info. Only serve data with known filing/publication dates prior to analysis date. |
| **Statements Without Filing Dates** | Withhold for past-dated runs. Cannot verify point-in-time integrity. |
| **Social Feed Limitations** | Acknowledge that social sentiment is not fully point-in-time for historical runs. State this limitation in the report. |
| **Unreadable Final Rating** | Record as **REVIEW**. Never downgrade to Hold. Inform operator to re-run or review manually. |
| **Malformed Numeric Fields** | Drop placeholders/percentages. Reduce formatted prices to numbers. Null out bad fields individually; do not fail the whole decision if other fields are correct. |
| **Holding Window Not Fully Traded** | Leave decision **Pending**. Retry on next pass. Do not score partial returns. |

---

## 6. Escalation & Exception Handling

*   **Missing Credentials:** Stop before run starts. List all missing flags/settings.
*   **Invalid Instrument:** Reject at input prompt and file-write stage.
*   **Future Date:** Reject immediately.
*   **Concurrent Settlement:** Enforce one settlement pass per log at a time. Write outcomes incrementally so interrupted passes retain finished work.
*   **Evaluation Cell Failure:** Record failure, continue sweep. Do not abort entire evaluation.

---

## 7. Gotchas

*   **Conflict ≠ Hold:** In debates, conflicting stances are expected. Managers must commit to the stronger side based on merit. Hold is only for genuinely balanced/thin evidence. Using Hold to avoid conflict is a failure mode.
*   **Point-in-Time Integrity:** A past-dated run must **only** see information public on that date. This includes withholding live company profiles and using "as filed" financials. Leaking future data invalidates the simulation.
*   **Holdings Distinction:** There are three distinct states: (1) Position held, (2) Explicitly flat book, (3) No portfolio provided. Treating "not provided" as "flat" invents facts about the user's account. Always distinguish these.
*   **Non-Determinism:** Two runs of the same input will likely differ due to AI sampling and live data changes. This is expected, not a bug. Lower temperature helps but does not guarantee reproducibility.
*   **Decision Log Idempotency:** One entry per instrument+date. Re-running the same analysis updates the existing entry (if pending) or creates a new one only if the date/instrument combo is unique. Do not duplicate entries for the same decision.
*   **Evaluation Independence:** Evaluation cells are independent. No position carries forward between cells. It scores decision quality, not portfolio P&L.
*   **Trader vs. PM Scale:** Trader uses 3 levels (Buy/Hold/Sell). Portfolio Manager uses 5 levels (Buy/Overweight/Hold/Underweight/Sell). Overweight maps to Buy, Underweight maps to Sell at the Trader level, but the finer distinction is reserved for the PM.
