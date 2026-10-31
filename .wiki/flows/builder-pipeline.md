---
title: "Builder pipeline (POC 3)"
type: "flow"
status: "active"
language: "default"
source_paths:
  - "poc/poc-3-builder-agent/src/main/java/com/embabel/smith/builder/BuilderAgent.java"
  - "poc/poc-3-builder-agent/src/main/java/com/embabel/smith/builder/DraftToSkillPack.java"
  - "poc/poc-3-builder-agent/src/main/java/com/embabel/smith/builder/BuilderApp.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/DraftQuality.java"
updated_at: "2026-11-02"
---

# Builder pipeline (POC 3)

POC 3 is **the builder** (design D2): a code-backed `@Agent`, GOAP-planned, with a single goal —
"a validated skill-pack exists on disk" (represented by `SkillPackRef`). Its pipeline is three
code-backed actions:

```
UserInput --compileFromDescription--> CompiledDraft
        --validateDraft-------------> ValidatedDraft
        --writeSkillPack------------> SkillPackRef   (goal)
```

The LLM is called **only** inside `compileFromDescription`. The validate and write steps are pure
Java, so the pipeline order is deterministic (GOAP matches the typed pre/postconditions) and the
offline behaviour is testable. The emitted *prompts*, however, remain model-generated.

## The three steps

1. **`compileFromDescription(UserInput)`** — *endpoint-gated.* Wraps Embabel's
   `DefaultNaturalLanguageCompiler.compileAgent(...)` to turn the description into an
   `AgentCompilationResult` (actions + goal + intermediate types + issues). `DraftToSkillPack`
   converts that Embabel-specific result into the format's `SkillPack` (its `normalizeGoalOutput`
   repairs degenerate void/no-op/scalar goals), so downstream steps work on the format's own
   types. It now also logs the draft's **goal-shape quality** (the `DraftQuality` classifier) before
   and after normalization — e.g. `raw=NO_OP_GOAL → normalized=HEALTHY` — making the "weak at
   source" draft weakness observable (doc 02 caveat #2). Throws if no `Ai` bean is present (the
   offline tests don't reach here). See [[nl-compile]] for the standalone de-risking POC.
2. **`validateDraft(CompiledDraft)`** — *pure.* Runs `SkillPackValidator` on the draft and returns
   a `ValidatedDraft(pack, valid, issues)`. An invalid draft is **reported, not silently
   accepted**. See [[validation-codes]].
3. **`writeSkillPack(ValidatedDraft)`** — *pure.* **Refuses to write an invalid draft** (throws
   "Refusing to write an invalid draft"). For a valid draft it writes via `SkillPackWriter`,
   re-reads the pack from disk, and **re-validates** what was written; if the written pack fails
   re-validation it throws. Returns a `SkillPackRef(path, name, version)`. This is the guardrail
   — see [[validate-before-write]].

## Where it writes

Built packs go to `builder.output-dir` (default `./built-skills`) under the pack's name:
`./built-skills/<name>/`. `BuilderApp.run()` deploys the agent via `AgentScopeBuilder.fromInstance`
and reports the produced `SkillPackRef`, or "No skill-pack produced" with the process status.

## The two invariants

- **Deterministic structure, model-generated content** (D2). Code-backed actions preserve the
  typed contract between steps; the alternative (LLM-backed actions) would make the pipeline order
  model-dependent — the non-determinism the builder explicitly avoids.
- **Validate-before-write** (D5). `writeSkillPack` runs only after `validateDraft` passes. This is
  the guard that makes "generate with no code" safe. See [[validate-before-write]].

The offline tests (`BuilderAgentTest`) exercise steps 2 and 3 directly against a hand-built agent
instance (with `ai == null`), confirming a consistent pack validates, a dangling type reference is
rejected, a valid draft writes and re-validates, and an invalid draft is refused with nothing
written.
