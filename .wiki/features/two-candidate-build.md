---
title: "Two-candidate build (single agent vs multi-agent process)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/Candidate.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/CandidateShape.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/CandidateRecommender.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/Recommendation.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/SingleShotProcessSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/ProcessRepair.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/ProcessSpec.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/ProcessValidator.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/ProcessValidationResult.java"
  - "openspec/specs/agent-spec-process/spec.md"
updated_at: "2026-11-15"
---

# Two-candidate build (single agent vs multi-agent process)

One description, two shapes. `BuilderService.build` compiles the same text twice — once into a
single-agent pack, once into a `ProcessSpec` (a named, ordered list of `AgentSpec`s) — scores both
against the same eval tasks, and publishes the winner. Decomposing is the better answer only when it
is both sound *and* measurably better, so the default is always the candidate the single-pack
validator already vouched for. See [[smith-builder]] for the pipeline this sits inside.

## The recommendation rule

`CandidateRecommender.recommend` picks the process **only** when it is non-null, `valid()`, and both
scores exist with `process.score() > single.score()`. Strictly greater: a tie recommends the single
agent, as does an invalid process or a missing candidate. `Recommendation` keeps all three (chosen,
single, process) plus a `processNote`, because the rejected candidate is the evidence that the choice
was made rather than assumed.

## Generation, validation, repair

`SingleShotProcessSpecGenerator` asks the model for the decomposition and **throws when it returns
fewer than two agents** — one pack is the other candidate, not a second shape of this one. The throw
lands in the `build`'s "candidate failed to generate" note; the build continues.

`ProcessValidator` (`smith-common`) is the check a single-pack validator cannot make, because it is
about hand-offs rather than any one agent: exported goal present, goal produced by the agent's own
steps, connected from `UserInput` to the final goal, every step input fed by some agent, goal names
unique across agents, and no cycle. Type names match by **short name**, so
`com.embabel.agent.domain.io.UserInput` and `UserInput` are the same hand-off. `BuilderService` then
adds one rule of its own: every pack of the process must also pass `AgentSpecValidator`, or the
runtime would refuse to ingest what the build just published.

A detected cycle gets one bounded repair — `ProcessRepair` reorders the agents topologically over the
data-flow graph, up to `MAX_ATTEMPTS = 3`, then re-validates. A reorder cannot fix data flow that is
itself circular; that case **blocks the process candidate only** (the ruling from the archived
`builder-process-target` change): the candidate is neither stored nor published, the report names the
cycle, and the build ships the single-agent spec.

## Gate, storage, publication

The score gate applies to the **recommended** candidate, not to each — what must not ship below the
gate is what the build ships. Both candidates are then stored (`BuilderAgent.writeProcess` refuses an
invalid process, so only valid ones reach disk); only the recommended one is published, as a
`ProcessPublishable` — `process.yml` plus one pack directory each, so a source pointed at the
directory ingests the multi-pack world the runtime composes ([[spec-sources]]).

The report carries the decision: `candidate:` is the recommendation's `reason()` (scores plus the
note, if any), and `candidates:` lists both scores, with `not scored` for a candidate that never got
a model call.

## Known residual

`ProcessValidationResult` reports `sharedTypes` (types used by more than one agent — the glue that
makes it a process rather than a pile of packs) but **nothing fails on it**. Two agents that share no
type still validate, so the spec's "two or more agent specs that share intermediate types" is enforced
only on the count, not the sharing.
