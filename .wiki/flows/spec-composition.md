---
title: "Spec composition (POC 6)"
type: "flow"
status: "active"
language: "default"
source_paths:
  - "poc/poc-6-agent-spec-composition/src/main/java/com/embabel/smith/composition/CompositionRunner.java"
  - "poc/poc-6-agent-spec-composition/src/main/java/com/embabel/smith/composition/CompositionApp.java"
  - "agent-specs/compose-extract/goal.yml"
  - "agent-specs/compose-explain/goal.yml"
updated_at: "2026-11-01"
---

# Spec composition (POC 6)

POC 6 is the **composition** claim: two or more agent specs, authored and validated *independently*,
are loaded into one `AgentPlatform`, and the non-LLM GOAP planner chains an action from each pack
through their **shared intermediate type** — no orchestration code. See [[exported-goal-as-spec]]
for why a shared type is enough for the planner to route across packs.

## The two sample packs

Composition is demonstrated with two packs that share the `TopicBrief` type:

- `agent-specs/compose-extract/` — `UserInput → extractTopic → TopicBrief`.
- `agent-specs/compose-explain/` — `TopicBrief → writeExplainer → BackgroundMessage`.

Neither pack knows the other exists. The only thing that binds them is that the first *produces*
`TopicBrief` and the second *consumes* it.

## How the run works

`CompositionRunner.compose(packDirs, targetGoal, input, plannerOverride)`:

1. **Load and validate every pack.** Each is read with `AgentSpecReader` and checked with
   `AgentSpecValidator`; any invalid pack throws before anything runs. See
   [[agent-spec-format]] and [[validation-codes]].
2. **Merge the types.** `combinedTypes(packs)` returns the built-in I/O types plus every pack's
   intermediate types, **deduplicated by name** — the shared `TopicBrief` is the composition's glue.
3. **Assemble one flat step directory.** `assembleCombined` copies each pack's `goal.yml` (renamed
   `goal-<i>.yml` to avoid cross-pack collision) and every `steps/*.yml` into a single temp
   directory, so all goals and actions are ingested — and thus planned — together. Source packs are
   untouched.
4. **Deploy and run.** `StepSpecAgentScopeBuilder.deploy()` ingests the merged specs (no
   compilation); the agent is narrowed to `targetGoal` and run over `UserInput`. The GOAP planner
   resolves the path `extractTopic → writeExplainer` across the packs.
5. **Extract the goal output.** `process.lastResult()` must be a `BackgroundMessage`.

The planner is always **GOAP** here (a per-run override is accepted, but composition needs the
deterministic planner). `CompositionApp` is the endpoint-gated launcher; it requires at least two
packs (`builder.pack-dirs`).

## What it proves

That specs compose by their typed pre/postconditions alone. The offline wiring is pinned by
`CompositionWiringTest`; the live run (goal achieved in ~14 s) is in `docs/04-poc-run-results.md`
§5. See [[what-the-pocs-prove]].
