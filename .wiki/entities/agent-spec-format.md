---
title: "The agent spec format"
type: "entity"
status: "active"
language: "default"
source_paths:
  - "docs/agent-spec-format.md"
  - "smith-common/src/main/java/com/embabel/smith/common/model/AgentSpec.java"
  - "agent-specs/explainer/manifest.yml"
  - "agent-specs/explainer/goal.yml"
updated_at: "2026-11-01"
---

# The agent spec format

An **agent spec** is the on-disk unit of capability. It is a *directory*, not a single file, and it
is **pure data** (YAML) — no compiled code, no build step. The builder emits one; the no-code
runtime ingests one with zero compilation. A pack exposes **exactly one** callable spec, and that
spec *is* the exported goal. See [[exported-goal-as-spec]].

## Layout

```
<pack>/
  manifest.yml          AgentSpecManifest
  goal.yml              GoalStepSpec   (export: true — the callable spec)
  steps/
    <name>.yml          ActionStepSpec (one file per action)
  types/
    <name>.yml          DynamicTypeDef (intermediate pure-data types)
```

`types/` may be empty (a `.gitkeep` is written to keep the directory). The format is owned by
`smith-common` (`com.embabel.smith.common.model` for the data model, `.io` for YAML I/O,
`.validation` for the checks). See [[agent-spec-model]].

## `manifest.yml`

Declares `name` and `version` (required), plus optional `defaultPlanner` (e.g. `GOAP`) and
`defaultModel`. The runtime honours these as per-pack defaults that a run may override. See
[[runtime-run]] for how the planner default is resolved.

## `goal.yml` — the exported goal

```yaml
stepType: goal
name: explainerComplete
description: "A complete explainer has been produced for the user"
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
export: true        # REQUIRED — this is what makes the goal a callable spec
```

`export: true` is what exposes the goal as a callable spec. A goal that is not exported does not
expose a spec and is **invalid** as an agent spec goal (the validator flags it as
`GOAL_NOT_EXPORTED`). `outputTypeName` is the type the runtime returns for a run.

## `steps/<name>.yml` — an action

```yaml
stepType: action
name: extractTopic
inputTypeNames:
  - com.embabel.agent.domain.io.UserInput
outputTypeName: TopicBrief
prompt: |
  ... User request: {{userInput.content}}
```

Optional, all nullable: `pre`, `post` (condition name lists), `cost`, `value`, `canRerun`,
`nullable`, `toolGroups`. A non-empty `toolGroups` list (e.g. `[shell]`) marks the action as
tool-backed rather than LLM-only — see [[runtime-adapter]] and [[trust-tiers]].

### Prompt references

The `prompt` is a **Jinja2** template. A reference `{{var}}` or `{{var.prop}}` must be **bound by
one of that action's declared `inputTypeNames`**, where `var` is the decapitalized short name of
the input type:

- input type `UserInput` → `{{userInput}}` or `{{userInput.content}}`
- input type `TopicBrief` → `{{topicBrief.topic}}`, `{{topicBrief.audience}}`, …

If `var.prop` is used and the input is a declared dynamic type, `prop` must be one of that type's
`ownProperties`. Framework I/O types (`UserInput`, `SystemInput`, `BackgroundMessage`,
`AssistantMessage`) are always available and need not be declared in `types/`. The validator
enforces both rules — see [[validation-codes]].

## `types/<name>.yml` — a pure-data type

```yaml
name: TopicBrief
ownProperties:
  - name: topic
    type: string
parents: []
creationPermitted: true
```

A `DynamicTypeDef` is a **pure-data** intermediate type: a name, a description, and named, typed
own-properties. No compiled JVM class is required — this is what makes an agent spec portable and
ingestible without a build. `type` is a plain scalar (`string`, `integer`, `number`, `boolean`, or
another type name).

## Validation

`AgentSpecValidator` checks, in order: layout, exactly-one exported goal, prompt references
resolve, and type references resolve. The full issue-code list is in [[validation-codes]]. The
sample `agent-specs/explainer/` is a worked example — see [[sample-explainer]].
