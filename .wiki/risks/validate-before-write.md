---
title: "Validate-before-write (D5)"
type: "risk"
status: "active"
language: "default"
source_paths:
  - "poc/poc-3-builder-agent/src/main/java/com/embabel/smith/builder/BuilderAgent.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/AgentSpecValidator.java"
  - "docs/05-architecture.md"
  - "docs/08-builder.md"
  - "poc/poc-3-builder-agent/src/test/java/com/embabel/smith/builder/BuilderAgentTest.java"
updated_at: "2026-11-26"
---

# Validate-before-write (D5)

The single invariant that makes "generate with no code" safe: **the builder must never write a
draft that fails validation.** Its durable statements are `docs/05-architecture.md` §Cross-cutting
invariants (one of the five, stated as "a generated spec is never written unless the deterministic
`AgentSpecValidator` passes it") and `docs/08-builder.md`, with
`docs/03-embabel-spec-agent-builder-design.md` for the thesis. The "D5" label is the numbering from
the builder's pre-consolidation design doc; `docs/` no longer numbers the decisions, so treat it as
this page's name rather than a pointer into a design document.

## Why it is load-bearing

The NL compiler is model-driven and **can emit wrong bindings and schemas** — a dangling type
reference, a prompt that references an undeclared input, a goal that isn't exported, a step whose
prompt never names the input it declares. If the builder wrote whatever the model produced, it would
ship broken agent specs that the runtime can't ingest. The validate-before-write guard is the fence
around that risk.

## How it is enforced

In `poc/poc-3-builder-agent/.../BuilderAgent.java`:

- `validateDraft` runs `AgentSpecValidator` and returns a `ValidatedDraft(pack, valid, issues)`. An
  invalid draft is *reported*, not silently accepted.
- `writeAgentSpec` **throws** if `draft.valid()` is false ("Refusing to write an invalid draft").
  For a valid draft it writes via `AgentSpecWriter`, **re-reads** the pack from disk, and
  **re-validates** what was actually written — so a pack that was valid in memory but corrupt on
  disk is also caught.

So there are two gates: the draft must validate *before* it is written, and the written pack must
re-validate *after*. See [[builder-pipeline]] and [[validation-codes]].

The guard belongs to the pipeline, not to one route through it: `smith build --flow` has four values
(`java`, `pack`, `agentic`, `harness`), and the `harness` flow — whose validator runs inside its own
generate stage — changes the recorded gate list and not the outcome: on a failing draft it stops at the
same `build` stage as the `java` flow and publishes nothing (`FlowGateParityTest`). The skill path
carries a second instance of the same invariant in another shape: `skill/SkillBuilderService` calls
`skill/SkillFileReferences.missing(skill)` just after the security scan and blocks at stage
`references`, before publishing, when the generated `SKILL.md` names a file the published folder will
not contain. Different checker, same posture — a reader's loader validates those references as it loads
and refuses the *whole* skill on a missing one, logging it where the builder cannot see it, so the
builder refuses to publish an artifact that would not load. See [[skill-output-target]].

## What would go wrong if it were removed

Removing the guard (a tempting shortcut to make a test pass or to "just get it to write") would let
malformed packs reach disk. They would then fail at runtime ingest — far from where the root cause
is — or worse, partially ingest and misbehave. The offline test `BuilderAgentTest` locks this in:
`writeAgentSpecRefusesAnInvalidDraft` asserts the throw **and** that nothing was written
(`out.toFile().list()` is empty).

## Do not

- Do not remove or bypass the `valid()` check in `writeAgentSpec`.
- Do not weaken `AgentSpecValidator` to make an invalid draft pass. If a legitimate pack is being
  rejected, fix the pack or the format contract (see [[agent-spec-format]]), not the guard.
