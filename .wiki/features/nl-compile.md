---
title: "NL compile (POC 2)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-2-nl-compile/src/main/java/com/embabel/smith/nlcompile/NlCompileRunner.java"
  - "poc/poc-2-nl-compile/src/main/java/com/embabel/smith/nlcompile/StepSpecContextFactory.java"
  - "poc/poc-2-nl-compile/src/main/java/com/embabel/smith/nlcompile/NlCompileApp.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/DraftQuality.java"
updated_at: "2026-11-02"
---

# NL compile (POC 2)

POC 2 de-risks the **natural-language → draft** path in isolation, before the builder (POC 3)
wraps it. It turns a plain-language description into an Embabel `AgentCompilationResult` via
Embabel's `DefaultNaturalLanguageCompiler`. Entry point:
`poc/poc-2-nl-compile/src/main/java/com/embabel/smith/nlcompile/NlCompileRunner.java`.

## What it does

`NlCompileRunner.compile(agentName, description)` returns an `AgentCompilationResult` containing:

- the **actions** (`PromptedActionSpec`s),
- the **exported goal**,
- the **intermediate types** the chain requires, and
- **issues to review** (`getErrors()`).

The critical design point: the result is a **draft, not a finished skill**. When the description is
ambiguous, the compiler returns a draft *together with explicit issues to review* rather than
silently guessing. This is what the builder later validates and refuses to write if invalid — see
[[builder-pipeline]] and [[validate-before-write]].

## The compile context

`StepSpecContextFactory.forAgent(...)` builds the `StepSpecContext` the compiler needs: the
platform's `DataDictionary` (the types the draft may reference) plus the available tool groups,
tools, and LLM references. The POC skill-packs are pure-LLM, so the tool/tool/reference lists are
empty. `NlCompileApp` is the launcher (endpoint-gated); it prints the actions, goal, intermediate
types, and issues — and now the draft's **goal-shape quality** via the `DraftQuality` classifier
(`smith-common`): a `HEALTHY`/degenerate verdict with a reason (doc 02 caveat #2 — "LLM-authored
skill quality is weak at the source"). The classifier detects `void`/no-op/scalar goals and the
*orphan* goal (no action produces the goal's output type, so the plan STUCKs). It is a read-only
measurement; the builder's `normalizeGoalOutput` is the part that repairs the deterministic
degenerate shapes — see [[builder-pipeline]].

## Relationship to the builder

POC 3's `compileFromDescription` uses the same `DefaultNaturalLanguageCompiler` and then maps the
result into the shared format via `DraftToSkillPack`. POC 2 exists so the NL→draft behaviour can be
verified on its own (tasks 3.1–3.2) — including that an ambiguous description yields a draft plus
issues rather than a silently-wrong draft — without the builder's validate/write steps in the way.
See [[architecture]] and [[what-is-embabel-smith]].
