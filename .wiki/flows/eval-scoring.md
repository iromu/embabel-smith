---
title: "Eval and scoring (POC 5)"
type: "flow"
status: "active"
language: "default"
source_paths:
  - "poc/poc-5-eval/src/main/java/com/embabel/smith/eval/GoalSkillEvaluator.java"
  - "poc/poc-5-eval/src/main/java/com/embabel/smith/eval/SkillEvalScorer.java"
  - "poc/poc-5-eval/src/main/java/com/embabel/smith/eval/AgentInvocationSkillInvoker.java"
  - "poc/poc-5-eval/src/main/java/com/embabel/smith/eval/PromptRewriteLoop.java"
updated_at: "2026-11-01"
---

# Eval and scoring (POC 5)

POC 5 adapts `embabel-agent-eval` (which is conversational/chatbot-oriented) to score a
**goal-type** skill — a skill invoked as a single typed goal run, not a chat session (design D6:
the eval is *adapted, not assumed*).

## The invocation seam

The key decoupling is the `SkillInvoker` functional interface: "run the goal-type skill on an
input and return the goal output's content."

- **Production (endpoint-gated):** `AgentInvocationSkillInvoker` runs the skill via Embabel's
  `AgentInvocation` (the non-chat path) and returns the `BackgroundMessage` content.
- **Offline:** tests inject a fake invoker, so the full evaluate → score → composite flow is
  verifiable without a live model.

`EvalApp` deploys the target skill reusing POC 1's `StepSpecAssembler` and `SkillPackTypeMapper`
(hence `poc-5-eval` depends on `poc-1-no-code-runtime`), then invokes it. See [[architecture]] and
[[runtime-run]].

## Scoring

- **`SkillEvalTask`** — one task: a user `input`, human-readable `acceptanceCriteria`, and an
  `acceptance` `Predicate<String>` checked against the goal output. `EvalApp` ships two sample
  tasks (coffee machine, black hole), each passing if the output contains the expected keyword.
- **`SkillEvalScorer.scoreOne`** — `1.0` when the goal output is present and satisfies the
  acceptance check, `0.0` otherwise.
- **`SkillEvalScorer.composite`** — the composite is the **mean** of the per-task assertion
  scores, with a failure count. The conversational-only dimensions (subjective tone, response
  times) are zeroed because they don't apply to a single-shot goal run. The result is expressed in
  the eval module's own `Score`/`FullScores` types so it is comparable to the conversational eval.
- **`GoalSkillEvaluator.evaluate`** — maps each task through the scorer and returns the composite.

## Stability measurement

`EvalApp` runs the same skill + tasks `BUILDER_EVAL_RUNS` times (default 5) and reports the
per-run composite scores' mean/min/max/range/stdev plus a stable/noisy verdict, via
**`ScoreStability`** (pure, offline). This closes docs/04 caveat #4 ("eval scores are only as
stable as the harness, which was not measured"): the non-LLM scoring is deterministic, so the
score is only as noisy as the LLM's outputs — for the explainer skill the range is 0 across runs,
a **stable, repeatable signal**. See [[what-the-pocs-prove]].

## The rewrite loop (stretch)

`PromptRewriteLoop` is the eval→rewrite half (task 6.2, marked stretch in the tasks file):

- **`rewritePrompt`** — *endpoint-gated.* Asks the LLM to revise the prompt so it handles a
  failing case better.
- **`applyRevisedPrompt`** — *pure.* Splices the revised prompt into the named step, rebuilds the
  `SkillPack`, and **re-runs the group-1 validation**. Nothing is written; the caller decides what
  to do with the result. The re-validation half is what is verified offline.

## What is primary

The **score** is the primary deliverable; the rewrite loop is demoted to a stretch goal if it
proves too costly (see the D6 rationale in
`openspec/changes/embabel-agent-builder/design.md`). Offline tests: `GoalSkillEvaluatorTest`,
`SkillEvalScorerTest`, `PromptRewriteLoopTest`, `ScoreStabilityTest`.
