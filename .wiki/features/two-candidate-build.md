---
title: "Two-candidate build (single-agent spec vs agents-pack)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/Candidate.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/CandidateShape.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/CandidateRecommender.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/Recommendation.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/SingleShotAgentsPackGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AgentsPackRepair.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/AgentsPack.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/AgentsPackValidator.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/AgentsPackValidationResult.java"
  - "docs/08-builder.md"
updated_at: "2026-11-26"
---

# Two-candidate build (single-agent spec vs agents-pack)

One description, two shapes. `BuilderService.build` compiles the same text twice — once into a
single-agent pack, once into an `AgentsPack` (a named, ordered list of `AgentSpec`s) — scores both
against the same eval tasks, and publishes the winner. Decomposing is the better answer only when it
is both sound *and* measurably better, so the default is always the candidate the single-pack
validator already vouched for. See [[smith-builder]] for the pipeline this sits inside.

## The recommendation rule

`CandidateRecommender.recommend` picks the agents-pack **only** when it is non-null, `valid()`, and
both scores exist with `process.score() > single.score()` (`process` is that method's own parameter
name). Strictly greater: a tie recommends the single agent, as does an invalid pack or a missing
candidate. `Recommendation` keeps all three (chosen, single, agentsPack) plus an `agentsPackNote`,
because the rejected candidate is the evidence that the choice was made rather than assumed.

## Generation, validation, repair

`SingleShotAgentsPackGenerator` asks the model for the decomposition and **throws when it returns
fewer than two agents** — one pack is the other candidate, not a second shape of this one. The throw
lands in the `build`'s "candidate failed to generate" note; the build continues. `smith build --flow pack`
does not change the pair: the flow branches only in `cli/BuildCommand.generators()`, while `newService`
wires `SingleShotAgentsPackGenerator` unconditionally (`cli/BuildCommand.java#newService`), so under
`pack` the agents-pack candidate is still generated Java-side and still spends Java-issued model calls
([[smith-builder]]).

`AgentsPackValidator` (`smith-common`) is the check a single-pack validator cannot make, because it is
about hand-offs rather than any one agent: exported goal present, goal produced by the agent's own
steps, connected from `UserInput` to the final goal, every step input fed by some agent, goal names
unique across agents, and no cycle. Type names match by **short name**, so
`com.embabel.agent.domain.io.UserInput` and `UserInput` are the same hand-off. `BuilderService` then
adds one rule of its own: every member agent spec of the pack must also pass `AgentSpecValidator`, or
the runtime would refuse to ingest what the build just published.

A detected cycle gets one bounded repair — `AgentsPackRepair` reorders the agents topologically over the
data-flow graph, up to `MAX_ATTEMPTS = 3`, then re-validates. A reorder cannot fix data flow that is
itself circular; that case **blocks the agents-pack candidate only**
(`docs/08-builder.md` §Agents-pack output records the ruling): the candidate is neither stored nor
published, the report names the cycle, and the build ships the single-agent spec.

## Gate, storage, publication

The score gate applies to the **recommended** candidate, not to each — what must not ship below the
gate is what the build ships. Both candidates are then stored (`BuilderAgent.writeAgentsPack` refuses
an invalid pack, so only valid ones reach disk); only the recommended one is published, as an
`AgentsPackPublishable` — `process.yml` plus one pack directory each, so a source pointed at the
directory ingests the multi-pack world the runtime composes ([[spec-sources]]).

The report carries the decision: `candidate:` is the recommendation's `reason()` (scores plus the
note, if any), and `candidates:` lists both scores, with `not scored` for a candidate that never got
a model call.

## Known residual

`AgentsPackValidationResult` reports `sharedTypes` (types used by more than one agent — the glue that
makes it an agents-pack rather than a pile of packs) but **nothing fails on it**. Two agents that share
no type still validate: the two-or-more-agents rule is enforced only on the count, by
`SingleShotAgentsPackGenerator` throwing below two, not on the sharing.
