---
title: "Skill lifecycle and drift detection (POC 18)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-18-skill-lifecycle/src/main/java/com/embabel/smith/lifecycle/LifecycleManager.java"
  - "poc/poc-18-skill-lifecycle/src/main/java/com/embabel/smith/lifecycle/DriftDetector.java"
  - "poc/poc-18-skill-lifecycle/src/main/java/com/embabel/smith/lifecycle/SkillContentHash.java"
  - "poc/poc-18-skill-lifecycle/src/main/java/com/embabel/smith/lifecycle/LifecycleState.java"
  - "poc/poc-18-skill-lifecycle/src/main/java/com/embabel/smith/lifecycle/LiveEvalRunner.java"
updated_at: "2026-11-04"
---

# Skill lifecycle and drift detection (POC 18)

POC 18 closes doc 03 §1's "Skill decay / lifecycle" gap (claim C17): a skill's **contract is its
eval pass-rate**, and *drift* is the divergence of its content or score from the recorded baseline.
The POC builds a SkillOps-style **proactive re-eval** that detects drift, re-scores, and moves the
skill through a lifecycle state machine.

## The lifecycle states

`LifecycleState` — `INCUBATING → ACTIVE → DEGRADED → RETIRED`:

- **INCUBATING** — a new skill with no recorded baseline.
- **ACTIVE** — baseline recorded, content and score stable.
- **DEGRADED** — drift detected (content changed or score dropped); re-evaluate before trusting.
- **RETIRED** — terminal; the skill's contract is broken (score collapsed).

## The two drift signals

**`SkillContentHash`** — a SHA-256 over the pack's prompt text, declared types, and tool groups.
Any prompt edit, type rename, or tool-group change produces a different hash → **content drift**.

**`DriftDetector`** — compares the current content hash and pass-rate against the recorded
baseline. Two independent signals:

- **Content drift** — the hash diverged (the skill's declared behaviour changed).
- **Score drift** — the pass-rate dropped below the baseline (the skill's actual behaviour
  degraded).

## The state machine (`LifecycleManager`)

`LifecycleManager.evaluate(skill, currentHash, currentScore)` produces a `DriftReport` with the
before/after state and a verdict:

- Clean baseline (first eval) → `INCUBATING → ACTIVE`.
- Content hash changed, score unchanged → `ACTIVE → DEGRADED` ("content changed; re-evaluate").
- Score dropped below threshold → `ACTIVE → DEGRADED` ("score dropped; re-evaluate").
- Score collapsed (pass-rate 0.0) → `ACTIVE → RETIRED` ("score collapsed; contract violated").
- `RETIRED` is terminal — no further transitions.

## Live re-eval (`LiveEvalRunner`)

The endpoint-gated half: deploys the skill as a throwaway agent, runs a set of eval tasks against
it, and measures the live pass-rate. Only skills whose actions use the built-in `UserInput` /
`BackgroundMessage` types are supported (the runner registers exactly those).

## What it proves

The deterministic walkthrough (no model) exercises the full lifecycle: healthy **100%** →
reworded (content drift) → regressed **0%** → `RETIRED`. The live re-eval confirms the same on a
real model. Seventeen offline tests pin the hash, both drift signals, and every state transition.
The lifecycle is **applied in the POC 15 builder UI**: the retrieval pane drops retired skills and
de-ranks degraded ones, and the curate re-eval endpoint moves a skill through the lifecycle.
See [[retrieval-shadowing]] and [[what-the-pocs-prove]].
