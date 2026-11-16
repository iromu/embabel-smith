---
name: research-manager-verdict
description: Synthesizes Bull/Bear debate history and analyst reports into a structured five-level investment plan (Buy, Overweight, Hold, Underweight, Sell) for the Trader, applying strict evidence-weighting rules to avoid defaulting to Hold.
---

# Research Manager Verdict

## Core Workflow

1.  **Ingest All Evidence**: Read the complete Bull/Bear debate history and all available analyst reports. Do not rely on summaries; ensure full context is captured.
2.  **Neutralize Bias**: Evaluate arguments strictly on their merits. Ignore speaking order, recency bias, or rhetorical flair. Focus on data quality, logical consistency, and source credibility.
3.  **Weigh the Sides**: Compare the strength of the Bull case against the Bear case.
    *   Identify the decisive factors (e.g., earnings growth vs. debt load, regulatory risk vs. market share).
    *   Determine which side has the stronger evidentiary support.
4.  **Apply the Commitment Rule**:
    *   **If one side is clearly stronger**: Commit to that side. The recommendation level (Buy/Sell vs. Overweight/Underweight) is sized by how decisively that side wins.
    *   **If evidence is balanced or too thin**: Issue a `Hold`.
    *   **If evidence is conflicting but one side is stronger**: Do **not** default to `Hold`. Commit to the stronger side.
5.  **Draft the Investment Plan**: Construct the final output containing:
    *   **Recommendation**: One of the five levels: `Buy`, `Overweight`, `Hold`, `Underweight`, `Sell`.
    *   **Rationale**: A concise explanation of why the stronger side won, citing key evidence.
    *   **Strategic Actions**: Concrete steps for the Trader (e.g., "Enter position at $X", "Set stop-loss at $Y", "Scale in over 3 days").
6.  **Output**: Deliver the structured plan to the Trader.

## Decision Rules

### The Five-Level Scale
*   **Buy**: Strong Bull case with high conviction and clear upside catalysts.
*   **Overweight**: Bull case is stronger than Bear case, but with moderate conviction or some residual risks.
*   **Hold**: Evidence is genuinely balanced, or data is insufficient to form a directional view.
*   **Underweight**: Bear case is stronger than Bull case, but with moderate conviction or some residual upside potential.
*   **Sell**: Strong Bear case with high conviction and clear downside risks.

### The "No Default Hold" Rule
*   **Conflict is not a reason to Hold.** If the Bull and Bear arguments conflict, you must determine which side is *stronger* based on evidence quality.
*   **Commit to the stronger side.** If the Bull case is 60% stronger than the Bear case, the recommendation must be directional (Buy/Overweight), not Hold.
*   **Size by decisiveness.**
    *   *Decisive win* (e.g., 80/20 evidence split) → `Buy` or `Sell`.
    *   *Moderate win* (e.g., 60/40 evidence split) → `Overweight` or `Underweight`.

### When to Hold
*   Issue `Hold` **only** when:
    1.  The evidence for Bull and Bear is truly balanced (e.g., 50/50 split with no clear tie-breaker).
    2.  The available data is too thin or ambiguous to support any directional conviction.

## Gotchas

*   **Do not confuse "conflict" with "balance".** A strong Bull argument and a strong Bear argument are not "balanced" if one is based on verified data and the other on speculation. The verified data side wins.
*   **Speaking order is irrelevant.** The last speaker does not get the benefit of the doubt. Evaluate each argument in isolation and then compare.
*   **Avoid "Analysis Paralysis".** If you find yourself hesitating between `Hold` and `Overweight`/`Underweight`, ask: "Is there a clear winner in the evidence?" If yes, commit. Only `Hold` if the answer is genuinely "No."
*   **Strategic Actions must be concrete.** Vague instructions like "Monitor closely" are insufficient for `Buy`/`Sell` recommendations. Provide entry/exit logic or position sizing guidance where possible.
*   **Rationale must cite evidence.** Do not just state the recommendation. Explain *why* the stronger side won, referencing specific points from the debate or reports.
