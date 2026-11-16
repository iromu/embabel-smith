---
title: "Exported goal as spec"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "docs/03-embabel-spec-agent-builder-design.md"
  - "poc/poc-4-agent-spec-format/src/main/java/com/embabel/smith/agentspecformat/SpecSurface.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/GoalStepSpec.java"
updated_at: "2026-11-01"
---

# Exported goal as spec

The single most important idea in this project: **an Embabel "spec" is an *exported goal*.** A
`GoalStepSpec` with `export: true` is a callable goal; the same goal with `export: false` is not
exposed as a spec at all.

This is the project's way of mapping the "skills, not agents" paradigm (from the agent-skills
literature) onto Embabel's existing primitives. The thesis in
`docs/03-embabel-spec-agent-builder-design.md` is that Embabel *already* is a spec runtime —
it just names the pieces differently:

| Agent-skills concept | Embabel primitive |
| --- | --- |
| A skill (folder with metadata + instructions) | An **exported goal** + its `PromptedActionSpec` chain + `DynamicType`s |
| Progressive disclosure (load only what's referenced) | **YAML ingestion** of only the referenced specs |
| Skill selection at scale | The **GOAP** planner matching typed pre/postconditions |
| Skill composition | GOAP planning a path across action pre/postconditions |

## What "exported" buys you

Because a spec *is* a goal, the platform's planner can treat it like any other goal. The
`outputTypeName` of the exported goal is the type a run returns — the "goal output". In the
sample pack, that is `BackgroundMessage`. See [[agent-spec-format]] and [[sample-explainer]].

Two consequences are worth internalizing:

1. **Exactly one spec per pack.** An agent spec exposes exactly one callable goal, and the
   validator rejects a pack whose goal is not exported (`GOAL_NOT_EXPORTED`). See
   [[validation-codes]].
2. **On-demand loading.** Since a spec is just a goal plus a chain of action steps, the runtime
   can load *only* the steps in that chain rather than the whole pack — the "progressive
   disclosure" the primitive enables. See [[spec-format-on-demand]].

## Where it is proven

- **POC 4** (`poc-4-agent-spec-format`) is the dedicated proof. `SpecSurface.callableSpec(pack)`
  returns the exported goal, or `null` when the goal is not exported (in which case "nothing is
  exposed"). `SpecSurface.actionChain(pack)` traces the ordered action chain the planner will
  walk from the goal's output type back to an entry type no step produces (e.g. `UserInput`). See
  [[spec-format-on-demand]].
- **POC 1** ingests and runs the exported goal as the keystone path — see [[runtime-run]].

## Why it matters for the builder

The builder's goal state is "a validated agent spec exists on disk", and an agent spec is only
usable if its goal is exported. So "exported goal = spec" is the target the whole
build→validate→write pipeline is aiming at. See [[builder-pipeline]] and [[validate-before-write]].
