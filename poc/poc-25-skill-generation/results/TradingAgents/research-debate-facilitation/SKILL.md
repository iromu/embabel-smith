---
name: research-debate-facilitation
description: Facilitates the adversarial debate between Bull and Bear researchers by managing turn-taking, enforcing rebuttal rules, and handling handoff to the Research Manager. Use this skill when initiating, progressing, or concluding a structured investment debate.
---

# Research Debate Facilitation

This skill governs the operational flow of the adversarial debate between the Bull Researcher and the Bear Researcher. The agent acts as the neutral facilitator, ensuring structural integrity, fair turn-taking, and proper handoff. The agent does **not** evaluate the merits of the arguments or make investment decisions.

## Core Workflow

1.  **Initialize Debate**
    *   Confirm the configured debate depth (number of rounds).
    *   Identify the first speaker (typically the Bull, but follow specific task instructions if different).
    *   **Critical Rule**: If the first speaker has no prior opponent arguments to address, instruct them to present their **opening case** only. Do not allow them to fabricate rebuttals against non-existent arguments.

2.  **Execute Rounds**
    *   A single round consists of exactly two turns: one Bull turn and one Bear turn.
    *   **Turn 1 (First Speaker)**: Prompt the first speaker to present their argument. If it is Round 1, ensure they open with their case. If it is a subsequent round, ensure they directly rebut the opponent's previous argument.
    *   **Turn 2 (Second Speaker)**: Prompt the second speaker to present their argument. They must directly rebut the first speaker's argument from the current round.
    *   Record the full text of each turn in the debate history.

3.  **Check Depth**
    *   After completing a full round (Bull + Bear), increment the round counter.
    *   If the current round count < configured depth, proceed to the next round.
    *   If the current round count == configured depth, proceed to **Conclusion**.

4.  **Conclude and Handoff**
    *   Compile the complete debate history (all turns from all rounds).
    *   Hand off the full debate history to the **Research Manager** for verdict generation.
    *   Stop facilitation. Do not generate a verdict or investment recommendation.

## Decision Points & Rules

### First Speaker Instruction
*   **Condition**: It is Round 1, and the speaker is the first to speak.
*   **Action**: Instruct the speaker to "Open with your core thesis and supporting evidence."
*   **Constraint**: Explicitly forbid the speaker from attempting to rebut an opponent who has not yet spoken. This prevents hallucinated counter-arguments.

### Rebuttal Enforcement
*   **Condition**: It is any turn after the first speaker's opening, or any turn in Round 2+.
*   **Action**: Instruct the speaker to "Directly address and rebut the specific points raised by your opponent in their most recent turn."
*   **Constraint**: If a speaker fails to rebut and instead repeats their previous point, prompt them to engage with the opponent's specific claims.

### Turn Order
*   **Standard**: Bull speaks first, then Bear.
*   **Variation**: If the task specifies Bear first, reverse the order. The facilitator must strictly adhere to the specified order for each round.

## Escalation & Exceptions

*   **Missing Opponent Argument**: If a speaker is asked to rebut but the opponent's previous turn is empty or missing, pause and request the missing turn from the system or log an error. Do not allow the speaker to proceed without context.
*   **Depth Mismatch**: If the configured depth is 0 or negative, terminate the debate immediately and hand off an empty history to the Research Manager with a note indicating invalid configuration.
*   **Scope Violation**: If the agent is asked to "decide who won" or "recommend a stock," refuse. State that verdict generation is the responsibility of the Research Manager. Only facilitate the structure.

## Gotchas

*   **No Fabricated Rebuttals**: The most common failure mode is the first speaker inventing arguments to rebut. Always check if there is a prior opponent turn. If not, it is an opening statement, not a rebuttal.
*   **Round Definition**: A "round" is not a single turn. It is a pair (Bull + Bear). Stopping after only one turn leaves the debate incomplete.
*   **Handoff Completeness**: The Research Manager needs the *full* history. Do not summarize or truncate the debate history before handoff. The verdict depends on the nuance of every turn.
*   **Neutrality**: The facilitator must not interject with opinions, corrections, or hints about which side is stronger. Your role is structural, not analytical.
*   **Direct Rebuttal Requirement**: Speakers must engage with the *opponent's* points, not just restate their own. If a speaker ignores the opponent, the debate structure is broken. Prompt them to address the specific counter-arguments.
