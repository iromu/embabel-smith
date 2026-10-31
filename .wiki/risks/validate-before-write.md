---
title: "Validate-before-write (D5)"
type: "risk"
status: "active"
language: "default"
source_paths:
  - "poc/poc-3-builder-agent/src/main/java/com/embabel/smith/builder/BuilderAgent.java"
  - "openspec/changes/embabel-agent-builder/design.md"
  - "poc/poc-3-builder-agent/src/test/java/com/embabel/smith/builder/BuilderAgentTest.java"
updated_at: "2026-11-01"
---

# Validate-before-write (D5)

The single invariant that makes "generate with no code" safe: **the builder must never write a
draft that fails validation.** This is design decision D5 in
`openspec/changes/embabel-agent-builder/design.md`.

## Why it is load-bearing

The NL compiler is model-driven and **can emit wrong bindings and schemas** — a dangling type
reference, a prompt that references an undeclared input, a goal that isn't exported. If the builder
wrote whatever the model produced, it would ship broken skill-packs that the runtime can't ingest.
The validate-before-write guard is the fence around that risk.

## How it is enforced

In `poc/poc-3-builder-agent/.../BuilderAgent.java`:

- `validateDraft` runs `SkillPackValidator` and returns a `ValidatedDraft(pack, valid, issues)`. An
  invalid draft is *reported*, not silently accepted.
- `writeSkillPack` **throws** if `draft.valid()` is false ("Refusing to write an invalid draft").
  For a valid draft it writes via `SkillPackWriter`, **re-reads** the pack from disk, and
  **re-validates** what was actually written — so a pack that was valid in memory but corrupt on
  disk is also caught.

So there are two gates: the draft must validate *before* it is written, and the written pack must
re-validate *after*. See [[builder-pipeline]] and [[validation-codes]].

## What would go wrong if it were removed

Removing the guard (a tempting shortcut to make a test pass or to "just get it to write") would let
malformed packs reach disk. They would then fail at runtime ingest — far from where the root cause
is — or worse, partially ingest and misbehave. The offline test `BuilderAgentTest` locks this in:
`writeSkillPackRefusesAnInvalidDraft` asserts the throw **and** that nothing was written
(`out.toFile().list()` is empty).

## Do not

- Do not remove or bypass the `valid()` check in `writeSkillPack`.
- Do not weaken `SkillPackValidator` to make an invalid draft pass. If a legitimate pack is being
  rejected, fix the pack or the format contract (see [[skill-pack-format]]), not the guard.
