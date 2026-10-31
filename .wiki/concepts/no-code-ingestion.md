---
title: "No-code ingestion"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SkillRunner.java"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/StepSpecAssembler.java"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SkillPackTypeMapper.java"
updated_at: "2026-11-01"
---

# No-code ingestion

The keystone claim of the whole project: a skill-pack is **pure data**, so the runtime can ingest
it and run it with **zero compilation**. The only compiled code is the launcher itself; everything
it executes is YAML.

## How it works

Embabel's `embabel-agent-spec` module ingests agents from YAML at runtime — no build step. The
runtime uses two classes to do it:

- **`YmlStepSpecRepository`** — reads the step-spec YAML files (the goal + action steps) from a
  directory.
- **`StepSpecAgentScopeBuilder.deploy()`** — turns those specs into a live `AgentScope` on the
  `AgentPlatform`.

The format is data; the runtime translates it (the "runtime seam"):

- `DynamicTypeDef` → Embabel `DynamicType` (own-properties → `ValuePropertyDefinition`).
- `ActionStepSpec` / `GoalStepSpec` → Embabel `PromptedActionSpec` / `GoalSpec`.
- The exported goal's `outputTypeName` is the type the runtime returns for a run.

See [[skill-pack-format]] for the on-disk shape and [[skill-pack-model]] for the model records.

## The two translation helpers

- **`SkillPackTypeMapper`** (in `poc-1-no-code-runtime`) maps the pack's pure-data
  `DynamicTypeDef`s to Embabel `DynamicType`s, and `allRuntimeTypes(pack)` returns the full set of
  domain types to register: the pack's intermediate types **plus** the built-in I/O types the
  steps and goal reference (`UserInput`, `BackgroundMessage`). This is the deliberate seam between
  the format owner (`smith-common`) and the Embabel runtime — the format stays a plain data model,
  only the runtime translates it. See [[architecture]].
- **`StepSpecAssembler`** flattens the pack into a single directory that `YmlStepSpecRepository`
  can read. A skill-pack keeps `goal.yml` at its root and the actions under `steps/`, but the
  repository loads from one directory — so the assembler copies `goal.yml` and every `steps/*.yml`
  into a fresh temp directory. The source pack is left untouched.

## Why this is the keystone

POC 1 (`poc-1-no-code-runtime`) is built first and verifies the ingest/deploy/run path before
anything else (see the rollout order in
`openspec/changes/embabel-agent-builder/design.md`). If a skill-pack can be ingested and run with
no compilation, the builder's job reduces to *emitting valid YAML* — which is exactly what
[[builder-pipeline]] does. The full run path is in [[runtime-run]].
