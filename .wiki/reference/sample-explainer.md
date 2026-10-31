---
title: "The explainer sample pack"
type: "reference"
status: "active"
language: "default"
source_paths:
  - "skill-packs/explainer/manifest.yml"
  - "skill-packs/explainer/goal.yml"
  - "skill-packs/explainer/steps/extractTopic.yml"
  - "skill-packs/explainer/steps/writeExplainer.yml"
  - "skill-packs/explainer/types/TopicBrief.yml"
  - "smith-common/src/main/java/com/embabel/smith/common/samples/SampleSkillPacks.java"
updated_at: "2026-11-01"
---

# The explainer sample pack

`skill-packs/explainer/` is the canonical, well-formed skill-pack the whole project is checked
against. It is the on-disk twin of `SampleSkillPacks.explainer()` in `smith-common`. See
[[skill-pack-format]] for the general shape and [[skill-pack-model]] for the model records.

## The chain

```
UserInput → extractTopic → TopicBrief → writeExplainer → BackgroundMessage
```

Two actions, one intermediate type, one exported goal. Read it top to bottom:

1. **`manifest.yml`** — `name: explainer`, `version: 1.0.0`, `defaultPlanner: GOAP`.
2. **`steps/extractTopic.yml`** — takes `UserInput`, outputs `TopicBrief`. Its prompt extracts
   topic / audience / tone from `{{userInput.content}}` (a framework I/O type, so not declared in
   `types/`).
3. **`types/TopicBrief.yml`** — a pure-data type with `topic`, `audience`, `tone` (all `string`).
   This is what makes the chain portable with no compiled class.
4. **`steps/writeExplainer.yml`** — takes `TopicBrief`, outputs `BackgroundMessage`. Its prompt
   references `{{topicBrief.topic}}`, `{{topicBrief.audience}}`, `{{topicBrief.tone}}` — each bound
   by the declared `TopicBrief` input and present on its `ownProperties`.
5. **`goal.yml`** — `export: true`, `outputTypeName: com.embabel.agent.spec.model.BackgroundMessage`.
   This exported goal is the skill; its output type is what a run returns. See
   [[exported-goal-as-skill]].

## Why it is useful to read

- It is the **fixture** for offline tests across `smith-common`, the builder, and the runtime, so
  every module is checked against the same sample.
- It is the default `builder.pack-dir` for POC 1, POC 4, and POC 5, so it is the pack those run
  commands execute. See [[runtime-run]], [[skill-format-on-demand]], and [[eval-scoring]].
- It is the **safe first change** in [[getting-started]]: editing `writeExplainer.yml`'s prompt
  changes data only and keeps validation green, because the referenced properties are unchanged.

If you are learning the format, read this directory first, then `docs/skill-pack-format.md`, then
`SkillRunner.java`.
