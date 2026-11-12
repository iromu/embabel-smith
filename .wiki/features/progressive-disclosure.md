---
title: "Progressive disclosure under load (POC 11)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-11-progressive-disclosure/src/main/java/com/embabel/smith/disclosure/PlanContextMeter.java"
  - "poc/poc-11-progressive-disclosure/src/main/java/com/embabel/smith/disclosure/ProgressiveDisclosureRunner.java"
  - "agent-specs/reference-heavy/steps/answerFromDomain.yml"
updated_at: "2026-11-01"
---

# Progressive disclosure under load (POC 11)

POC 11 is the **progressive-disclosure** stress test (doc 02 §2, doc 03 §4.2): it shows that the
context a spec's **plan** carries stays bounded while the spec's on-disk knowledge grows
unbounded. Loading on demand, not eagerly, is what keeps context cost flat as a bundled reference
grows. Fully **offline** — a measurement POC, not a runtime feature.

## Two distinct contexts

`PlanContextMeter` separates the two contexts a spec carries:

- **Plan context** — what the GOAP planner loads: the goal and each action's
  name / description / types. It **never** includes the action prompt or any bundled reference, so
  it is bounded regardless of how much reference material the spec ships.
- **Execution context** — the prompt an action sends to the LLM. Under **eager** disclosure the
  bundled reference is inlined here (grows with the reference); under **progressive** disclosure it
  is fetched on demand, so the base prompt stays small.

## The measurement

`ProgressiveDisclosureRunner.measure(pack, reference)` returns a `ContextCost`:

- `planContextChars` — bounded, independent of the reference.
- `progressivePromptChars` — base prompt only (reference on demand).
- `eagerPromptChars` — base prompt + inlined reference.
- `eagerToProgressiveRatio()` — how many times larger the eager execution context is.

The sample pack `agent-specs/reference-heavy/` carries a `references/domain.md` that the
`answerFromDomain` action is told to consult **on demand** ("load only the part you need … do not
paste the guide").

## Result

As the reference grows **3.1 KB → 313.1 KB (×100)**, the plan context stays **constant at 280
chars** and the progressive prompt **constant at 263 chars**, while the eager prompt climbs
3,469 → 320,863 chars and the eager/progressive ratio climbs **13.2× → 1,220×**. The full numbers
are in `docs/04-poc-run-results.md` §5. See [[exported-goal-as-spec]] for the on-demand primitive
this builds on and [[what-the-pocs-prove]].
