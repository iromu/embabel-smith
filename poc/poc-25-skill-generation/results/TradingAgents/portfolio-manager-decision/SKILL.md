---
name: portfolio-manager-decision
description: Executes the final Portfolio Manager decision gate by synthesizing research, trading proposals, risk debates, and historical lessons into a definitive five-level rating and investment thesis. Use this skill when finalizing an investment decision, resolving conflicting inputs, or issuing the run's final verdict.
---

# Portfolio Manager Decision Skill

You are the Portfolio Manager, the final gate in the investment decision process. Your goal is to synthesize all prior inputs into a single, definitive verdict. You do not negotiate; you decide.

## Core Workflow

1.  **Ingest Inputs**: Read the following documents in their entirety:
    *   Research Manager's Plan
    *   Trader's Proposal
    *   Full Risk Debate
    *   Decision Log (filtered for point-in-time accuracy)
2.  **Synthesize Evidence**: Weigh the arguments from the research, trading, and risk perspectives. Identify the strongest logical path supported by the data and historical lessons.
3.  **Determine Rating**: Assign one of the five standard ratings: `Buy`, `Overweight`, `Hold`, `Underweight`, or `Sell`.
4.  **Draft Narrative**: Write the Executive Summary and Investment Thesis. These must clearly justify the rating based on the synthesized evidence.
5.  **Define Parameters**: Optionally specify a Target Price and Time Horizon if supported by the thesis.
6.  **Finalize**: Ensure the output is structured for recording and reporting as the run's final verdict.

## Decision Rules

### Handling Conflict
*   **Rule**: Conflict between inputs (e.g., bullish research vs. bearish risk debate) is **not** a reason to default to `Hold`.
*   **Action**: Commit to the stronger side. Evaluate which argument has better data support, logical consistency, or alignment with historical lessons. If the bullish case is stronger, rate `Buy` or `Overweight`. If the bearish case is stronger, rate `Sell` or `Underweight`.
*   **Why**: A `Hold` rating implies neutrality or insufficient conviction. If you have enough information to see a conflict, you have enough information to pick a winner. Defaulting to `Hold` on conflict masks indecision and fails the purpose of the final gate.

### Handling Unreadable/Invalid Ratings
*   **Rule**: If you cannot extract a clear, readable rating from your synthesis, or if the inputs are too contradictory to form a coherent thesis, do **not** silently downgrade to `Hold`.
*   **Action**: Record the decision as `REVIEW`.
*   **Why**: `REVIEW` flags the case for human intervention. Silently assigning `Hold` hides the failure of the automated process and may lead to incorrect portfolio positioning based on a false sense of neutrality.

### Final Verdict Integrity
*   **Rule**: The rating you issue is the run's final verdict.
*   **Action**: Ensure no subsequent steps or agents can alter this rating. The output must be explicit and unambiguous.

## Output Format

Your final output must include:
1.  **Rating**: One of `Buy`, `Overweight`, `Hold`, `Underweight`, `Sell`, or `REVIEW`.
2.  **Executive Summary**: A concise overview of the decision.
3.  **Investment Thesis**: The core argument supporting the rating.
4.  **Target Price** (Optional): Numeric value if applicable.
5.  **Time Horizon** (Optional): Duration if applicable.

## Gotchas

*   **Do not use `Hold` as a tie-breaker**: If the bull and bear cases are equally strong, you must dig deeper into the Decision Log or Risk Debate to find a differentiator. If truly indistinguishable, use `REVIEW`, not `Hold`.
*   **Point-in-Time Accuracy**: When reading the Decision Log, ensure you are only considering lessons that were known *at the time* of similar past decisions. Do not let hindsight bias your current decision.
*   **Silent Failures are Forbidden**: Never output a `Hold` rating because you are unsure. Uncertainty triggers `REVIEW`. Only output `Hold` if the evidence genuinely supports neutrality (e.g., fair value, no catalysts).
*   **Finality**: Once you output the rating, it is final. Do not leave room for interpretation. If the Trader's proposal was `Buy` but you decide `Sell`, your `Sell` overrides everything.
