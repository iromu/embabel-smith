---
name: memory-lesson-retrieval
description: Retrieves and formats settled decision lessons from the decision log for the Portfolio Manager, enforcing point-in-time integrity and excluding pending entries. Use when the Portfolio Manager requires historical context, recent instrument-specific lessons, or cross-instrument insights for a specific run date.
---

# Memory Lesson Retrieval

This skill governs the retrieval of historical decision lessons to inform the Portfolio Manager's current decision-making process. The primary objective is to provide high-quality, settled insights while strictly maintaining temporal integrity.

## Core Workflow

1.  **Identify Run Context**
    *   Determine the `run_date` for the current Portfolio Manager session.
    *   Identify the `target_instrument` (if applicable) for instrument-specific queries.

2.  **Query Decision Log**
    *   Execute queries against the decision log to retrieve candidate lessons.
    *   Perform two distinct retrieval passes:
        *   **Instrument-Specific:** Retrieve the most recent settled decisions for the `target_instrument`.
        *   **Cross-Instrument:** Retrieve recent settled lessons from other instruments to identify broader market patterns or strategy adjustments.

3.  **Filter for Settlement Status**
    *   Inspect the status of every retrieved entry.
    *   **Exclude** any entry where the status is `pending`, `open`, or `in-progress`.
    *   **Include** only entries where the status is `settled` or `closed`.
    *   *Reasoning:* Pending decisions have not yet realized their outcome. Using them as "lessons" introduces noise and unverified assumptions into the Portfolio Manager's prompt.

4.  **Enforce Point-in-Time Integrity**
    *   For each candidate lesson, compare the `outcome_known_date` (the date the result of the decision became known) against the `run_date`.
    *   **Include** the lesson only if `outcome_known_date` <= `run_date`.
    *   **Exclude** the lesson if `outcome_known_date` > `run_date`.
    *   *Reasoning:* This prevents "look-ahead bias." The Portfolio Manager must not be influenced by information that was not available at the time of the simulated or actual run.

5.  **Format for Prompt Injection**
    *   Structure the filtered lessons into a concise, readable format suitable for LLM prompt injection.
    *   Include key metadata: `decision_id`, `instrument`, `action_taken`, `outcome_summary`, and `lesson_learned`.
    *   Order lessons by relevance (most recent first) or by impact score if available.

6.  **Deliver to Portfolio Manager**
    *   Return the formatted string block to the Portfolio Manager agent.

## Decision Points & Rules

### Handling Empty Results
*   **If no settled lessons are found for the target instrument:**
    *   Proceed to the cross-instrument query.
    *   If cross-instrument lessons are also empty, return a standard "No historical lessons available" message. Do not fabricate lessons.

### Handling Conflicting Dates
*   **If `outcome_known_date` is missing:**
    *   Treat the entry as `pending` and exclude it. A missing outcome date implies the outcome is not yet verified or recorded.

### Cross-Instrument Relevance
*   **When selecting cross-instrument lessons:**
    *   Prioritize lessons from instruments with high correlation to the `target_instrument` if correlation data is available in the log.
    *   Otherwise, prioritize by recency of `outcome_known_date`.

## Escalation & Exceptions

*   **Data Integrity Errors:** If the decision log returns entries with inconsistent status fields (e.g., `status: settled` but `outcome_known_date` is null), escalate to the Data Quality team. Do not attempt to guess the status.
*   **Query Timeout:** If the decision log query exceeds the timeout threshold, return an empty lesson set with a warning flag. Do not block the Portfolio Manager's execution.

## Gotchas

*   **Settled vs. Closed:** A decision may be marked `closed` administratively but still have `pending` outcomes if the final P&L is not yet reconciled. Always verify the `outcome_known_date` is populated and valid, not just the status flag.
*   **Timezone Alignment:** Ensure `run_date` and `outcome_known_date` are compared in the same timezone (typically UTC). A lesson settled at 23:00 UTC on the day before the `run_date` is valid; one settled at 01:00 UTC on the `run_date` is also valid. Misalignment here causes subtle look-ahead bias.
*   **The "Recent" Trap:** "Most recent" refers to the `outcome_known_date`, not the `decision_creation_date`. A decision made 6 months ago but settled yesterday is a more recent lesson than one made yesterday but still pending.
*   **Prompt Bloat:** Limit the number of injected lessons (e.g., top 5-10) to prevent context window overflow. If more are available, prioritize by recency and relevance to the current market regime.
