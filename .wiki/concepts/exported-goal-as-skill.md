---
title: "Exported goal as skill"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "docs/03-embabel-skills-agent-builder-design.md"
  - "poc/poc-4-skill-format/src/main/java/com/embabel/smith/skillformat/SkillSurface.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/GoalStepSpec.java"
updated_at: "2026-11-01"
---

# Exported goal as skill

The single most important idea in this project: **an Embabel "skill" is an *exported goal*.** A
`GoalStepSpec` with `export: true` is a callable skill; the same goal with `export: false` is not
exposed as a skill at all.

This is the project's way of mapping the "skills, not agents" paradigm (from the agent-skills
literature) onto Embabel's existing primitives. The thesis in
`docs/03-embabel-skills-agent-builder-design.md` is that Embabel *already* is a skills runtime —
it just names the pieces differently:

| Agent-skills concept | Embabel primitive |
| --- | --- |
| A skill (folder with metadata + instructions) | An **exported goal** + its `PromptedActionSpec` chain + `DynamicType`s |
| Progressive disclosure (load only what's referenced) | **YAML ingestion** of only the referenced specs |
| Skill selection at scale | The **GOAP** planner matching typed pre/postconditions |
| Skill composition | GOAP planning a path across action pre/postconditions |

## What "exported" buys you

Because a skill *is* a goal, the platform's planner can treat it like any other goal. The
`outputTypeName` of the exported goal is the type a run returns — the "goal output". In the
sample pack, that is `BackgroundMessage`. See [[skill-pack-format]] and [[sample-explainer]].

Two consequences are worth internalizing:

1. **Exactly one skill per pack.** A skill-pack exposes exactly one callable skill, and the
   validator rejects a pack whose goal is not exported (`GOAL_NOT_EXPORTED`). See
   [[validation-codes]].
2. **On-demand loading.** Since the skill is just a goal plus a chain of action steps, the runtime
   can load *only* the steps in that chain rather than the whole pack — the "progressive
   disclosure" the primitive enables. See [[skill-format-on-demand]].

## Where it is proven

- **POC 4** (`poc-4-skill-format`) is the dedicated proof. `SkillSurface.callableSkill(pack)`
  returns the exported goal, or `null` when the goal is not exported (in which case "nothing is
  exposed"). `SkillSurface.actionChain(pack)` traces the ordered action chain the planner will
  walk from the goal's output type back to an entry type no step produces (e.g. `UserInput`). See
  [[skill-format-on-demand]].
- **POC 1** ingests and runs the exported goal as the keystone path — see [[runtime-run]].

## Why it matters for the builder

The builder's goal state is "a validated skill-pack exists on disk", and a skill-pack is only
usable if its goal is exported. So "exported goal = skill" is the target the whole
build→validate→write pipeline is aiming at. See [[builder-pipeline]] and [[validate-before-write]].
