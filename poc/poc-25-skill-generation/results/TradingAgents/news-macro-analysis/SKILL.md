---
name: news-macro-analysis
description: Generates a point-in-time news and macroeconomic analysis report for trading by retrieving company news, global headlines, FRED macro indicators, and Polymarket probabilities, strictly clamped to the analysis date.
---

# News & Macro Analysis

This skill defines the procedure for the News/Macro Analyst role. The goal is to produce a report on the current state of the world relevant to trading, ensuring strict point-in-time integrity.

## Core Workflow

1.  **Establish Analysis Date**: Identify the specific `analysis_date` for the report. All data retrieval must be clamped to this date.
2.  **Retrieve Core News Data (Critical)**:
    *   Fetch company-specific news and global/macro headlines.
    *   **Constraint**: Ensure all retrieved items were publicly available on or before the `analysis_date`.
    *   **Failure Handling**: If core news data cannot be retrieved, **abort the run** and report the failure loudly. This is a critical dependency.
3.  **Retrieve Macro Indicators (Optional)**:
    *   Fetch relevant macroeconomic indicators from FRED.
    *   **Constraint**: Clamp data to the `analysis_date`. Do not include future releases or revisions made after this date.
    *   **Failure Handling**: If FRED data is unavailable, mark this section as "Unavailable" in the report and proceed. Do not abort.
4.  **Retrieve Market-Implied Probabilities (Optional)**:
    *   Fetch live market-implied probabilities from Polymarket.
    *   **Constraint**: Ensure probabilities reflect the state of the market on the `analysis_date`.
    *   **Failure Handling**: If Polymarket data is unavailable, mark this section as "Unavailable" in the report and proceed. Do not abort.
5.  **Synthesize Report**:
    *   Compile the retrieved data into a coherent report.
    *   Clearly distinguish between core news, macro indicators, and prediction market data.
    *   Explicitly note any sections marked as "Unavailable" due to optional data failures.

## Decision Points & Rules

### Point-in-Time Integrity
*   **Rule**: All data must be clamped to the `analysis_date`.
*   **Why**: Trading decisions must be based on information available at the time of analysis. Including future data (look-ahead bias) invalidates the analysis.
*   **Action**: Filter out any news articles, macro releases, or market odds that were published or updated after the `analysis_date`.

### Data Availability & Failure Handling
*   **Core News (Company/Global)**:
    *   **Status**: Critical.
    *   **Action on Failure**: Abort the entire process. Report the error clearly.
    *   **Reason**: Without news, the analyst cannot assess the current state of the world. The report is useless without this foundation.
*   **Macro Indicators (FRED)**:
    *   **Status**: Optional Enrichment.
    *   **Action on Failure**: Proceed with the report. Include a placeholder stating "Macro data unavailable."
    *   **Reason**: While valuable, macro data is supplementary. The core news narrative can still provide actionable insights.
*   **Prediction Markets (Polymarket)**:
    *   **Status**: Optional Enrichment.
    *   **Action on Failure**: Proceed with the report. Include a placeholder stating "Prediction market data unavailable."
    *   **Reason**: Market probabilities are a secondary signal. Their absence does not invalidate the primary news analysis.

## Gotchas

*   **Look-Ahead Bias is Fatal**: A common mistake is retrieving the *latest* available data rather than the data available *on the analysis date*. Always verify the publication timestamp against the `analysis_date`. If a macro indicator was revised after the analysis date, use the original value available on that date, not the revision.
*   **Silent Failures are Dangerous**: Do not silently ignore missing macro or prediction market data. Explicitly state "Unavailable" in the report. This alerts the user that the analysis is incomplete but still valid based on available data.
*   **News is Non-Negotiable**: Unlike macro or prediction data, missing news data must cause a hard failure. Do not attempt to generate a report with missing core news; it violates the fundamental purpose of the skill.
*   **Polymarket Data Volatility**: Prediction market odds can change rapidly. Ensure the snapshot taken corresponds exactly to the `analysis_date` timestamp, not the current real-time state if the analysis is historical.
