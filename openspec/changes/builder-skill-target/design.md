# Design

## Context

The builder's `BuilderService.build` is a fixed five-stage pipeline for a single `AgentSpec` pack
(compile → validate/write → eval+refine → security → lifecycle → publish). POC 25 already
implements the skill pipeline as a POC: `SkillGenerator` (description → `SKILL.md`),
`SkillMdValidator` (frontmatter), `EvalSuite`/`EvalSource` (an `evals.json` suite), `EvalRunner`
(with-skill vs no-skill arms), `SkillGrader` (LLM-as-judge), and `Benchmark` (with vs without pass
rate → lift). This change brings that pipeline into the builder as a second output target. See
proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A `target=skill` build produces a validated, published skill folder.
- The skill path reuses the builder's publish (folder/git), and where sensible its security and
  lifecycle stages, so a published skill has the same collision / no-push / lifecycle semantics as a
  published spec.
- The skill gate is the with/without-skill lift, not the spec's "all deterministic tasks pass".

**Non-Goals:**
- Changing the spec target (change A owns the generation technique; change C owns multi-agent).
- Editing the skill at runtime or a skill self-improvement loop (out of scope here).

## Decisions

- **A parallel `SkillBuilderService`, not an overloaded `BuilderService.build`.** The skill path
  differs from the spec path in its output (a `SKILL.md` + `evals.json`, not a pack directory), its
  eval (with/without-skill lift, LLM-as-judge), and its gate (lift ≥ margin). A separate service
  keeps the deterministic spec pipeline untouched. The shared `BuildOptions` gains a `target`
  selector; the CLI dispatches to the right service.
- **Generalize the publisher to "a named directory."** Today `FolderPublisher`/`GitPublisher`
  publish an `AgentSpec` pack directory. The skill is a different directory shape. The publisher is
  generalized to publish an arbitrary named directory of files (the spec writer emits the pack dir;
  a skill writer emits the skill dir), so the collision gate and no-push-by-default semantics are
  shared rather than duplicated.
- **Model access via the builder's `Ai`.** Skill generation, the with-skill run, the no-skill run,
  and the LLM-as-judge grading all go through the builder's existing `Ai`/endpoint, keeping one
  model layer.
- **Reuse the security gate and lifecycle stamp.** The static prompt-injection scan applies to the
  `SKILL.md` body; the lifecycle sidecar records the content hash, the with-skill pass rate as the
  baseline, and the initial `incubating` state.

## Risks / Trade-offs

- [LLM-as-judge eval is non-deterministic] → The gate is a relative lift with a configurable
  minimum, not an exact score; the report carries the per-scenario grading so a blocked build is
  inspectable. This is a deliberate, documented departure from the spec path's deterministic gate.
- [The skill is a description, not an executable spec] → It improves a general agent's
  understanding but does not itself run a process; the lift measures that improvement, and the
  report states the limit.

## Resolved Decisions

- **Eval arms:** one — the builder's `Ai` model layer only (no separate raw arm). The lift is
  measured on the product model layer; the builder does not re-prove transport-independence (that was
  POC 25's purpose, not the product's).
- **Evals source:** generated from the description — the model drafts the `evals.json` suite
  (`generate_evals`). The builder gets a free-form description, not a known blueprint.
- **Lift gate:** any positive lift (`> 0`) — publish when the with-skill pass rate beats the no-skill
  baseline at all.
- **Lifecycle + security:** reuse both — the lifecycle sidecar records the with-skill pass rate as
  the baseline (initial state `incubating`), and the static prompt-injection scan applies to the
  `SKILL.md` body.
