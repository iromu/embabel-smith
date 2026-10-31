---
title: "Skill format: on-demand load (POC 4)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-4-skill-format/src/main/java/com/embabel/smith/skillformat/SkillSurface.java"
  - "poc/poc-4-skill-format/src/main/java/com/embabel/smith/skillformat/OnDemandAssembler.java"
  - "poc/poc-4-skill-format/src/main/java/com/embabel/smith/skillformat/SkillFormatApp.java"
updated_at: "2026-11-01"
---

# Skill format: on-demand load (POC 4)

POC 4 confirms the **exported-goal-as-skill** primitive: an exported goal is a callable skill whose
action chain can be **loaded and planned on demand**, while a goal declared `export: false` is not
exposed as a callable skill at all. See [[exported-goal-as-skill]].

## Resolving the skill and its chain

`SkillSurface` (in `poc-4-skill-format`) does two things:

- **`callableSkill(pack)`** — returns the pack's exported goal, or `null` when the goal is not
  exported (in which case "no callable skill is exposed").
- **`actionChain(pack)`** — traces the **ordered action chain** the planner will walk, from the
  goal's output type back through the steps that produce each needed type, until it reaches a type
  no step produces (the entry, e.g. `UserInput`). It assumes a single-input chain, which is the
  common case for POC skill-packs.

## Loading only the referenced steps

`OnDemandAssembler` copies the goal and **only** the action steps in the resolved chain (not the
whole pack) into a flat temp directory for `YmlStepSpecRepository`. This is the "progressive
disclosure" the exported-goal primitive enables — only the referenced steps are ingested. Compare
with POC 1's `StepSpecAssembler`, which copies *all* steps; see [[no-code-ingestion]] and
[[runtime-run]].

## The run

`SkillFormatApp` (endpoint-gated) reads and validates the pack, resolves the skill and chain,
registers the runtime types, assembles the on-demand steps, deploys via
`StepSpecAgentScopeBuilder`, and runs a GOAP plan over the input. It expects the plan to resolve to
the goal's output type (`BackgroundMessage`) and prints the result; otherwise it throws with the
process status.

## What it proves

Two tasks (4.1–4.2 in the tasks file):

1. A single exported goal loads on demand and the plan resolves to the goal's output type.
2. A goal declared `export: false` is **not** exposed as a callable skill.

Offline tests: `SkillSurfaceTest` (chain tracing, callable-skill detection) and
`OnDemandAssemblerTest`. This POC is the cleanest demonstration that "skill = exported goal" is a
real, loadable primitive rather than just a naming choice. See [[exported-goal-as-skill]].
