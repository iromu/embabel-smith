---
title: "Eval-guided refinement (POC 9)"
type: "flow"
status: "active"
language: "default"
source_paths:
  - "poc/poc-9-quality-refinement/src/main/java/com/embabel/smith/refinement/RefinementRunner.java"
  - "poc/poc-9-quality-refinement/src/main/java/com/embabel/smith/refinement/PromptRewriter.java"
  - "poc/poc-9-quality-refinement/src/main/java/com/embabel/smith/refinement/TaglineSpec.java"
  - "poc/poc-9-quality-refinement/src/main/java/com/embabel/smith/refinement/RefinementScorer.java"
updated_at: "2026-11-01"
---

# Eval-guided refinement (POC 9)

POC 9 is the **SkillAxe** loop (doc 03 §5): evaluate a weak LLM-authored spec, rewrite its prompt
from the failing tasks, re-evaluate, and report the before/after composite score. It answers the
literature caveat that LLM-authored specs are weak at the source — the POC shows the eval-guided
rewrite **repairs** it (a weak tagline spec goes 0% → 100%). Endpoint-gated (the spec runs and
the rewrite both call the model); the scoring and re-validation halves are pure.

## The loop (`RefinementRunner.improve`)

1. **Score the weak spec.** Deploy the pack, run each eval task, and produce a composite score via
   `RefinementScorer` (the same score/composite shape as POC 5's `SpecEvalScorer` — see
   [[eval-scoring]]).
2. **Find the failing tasks.** Tasks whose assertion score is below `1.0`. If none fail, the spec
   is already good and the loop is a no-op.
3. **Rewrite the prompt.** `PromptRewriter.rewrite` (*endpoint-gated*) asks the model — via the
   OpenAI-compatible `chat/completions` endpoint — to revise the named step's prompt so it satisfies
   the failing criteria, preserving the `{{...}}` template variables.
4. **Apply and re-validate.** `PromptRewriter.applyRevisedPrompt` (*pure*) splices the revised
   prompt into the step, rebuilds the `AgentSpec`, and re-runs `AgentSpecValidator`. If the revised
   pack fails validation, the loop throws — nothing is accepted. See [[validate-before-write]].
5. **Re-score.** Deploy the revised pack, run the same tasks, and compare. The outcome is a
   `RefinementOutcome(beforeScore, afterScore, revisedPrompt, failingInputs)`.

## The deliberately weak spec

`TaglineSpec.weak()` is a tagline writer whose prompt *forbids* the brand name ("do not include
the brand name"), while its eval tasks require the tagline to include "Nimbus". So the eval
reliably **fails before** the rewrite (both tasks missing the brand name → 0%) and **passes after**
the model adds "the tagline must explicitly include the brand name" (→ 100%).

## What it proves

That the eval→rewrite loop works end-to-end and *does* repair an LLM-authored spec. The caveat
(see [[what-the-pocs-prove]]): this is a **repair loop**, not a fix to the NL compiler — the raw
draft is still the weak thing being repaired. Offline tests cover the scorer and the re-validation
half; the live run is in `docs/04-poc-run-results.md` §5.
