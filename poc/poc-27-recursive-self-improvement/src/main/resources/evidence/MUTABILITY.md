# What a generation may change, and what it may not

This file is the single source for the mutable/fixed boundary. Later classes of this POC cite it
rather than restating it, and a run may not start without it. An attempt to mutate anything on the
fixed side stops the run, names the artifact, and records no score for that generation.

The boundary is the one the change's design records as **D1 — The regress stops at the interpreter**:

```
 MUTABLE -- generated, versioned, gated ------------------------------+
   generator prompts  ·  decomposition  ·  skill scripts (JS)         |
   process shape (N agent specs)  ·  detection policy                 |
   refinement policy  ·  the improver expressed as a pack             |
   builder packs  ·  their step prompts (4 packs)                     |
+---------------------------------------------------------------------+
 IMMUTABLE -- the kernel the claim is relative to ---------------------+
   Embabel AgentPlatform + the no-code runtime                        |
   EvalScorer (statically called inside GateChain.reeval)             |
   the frozen task set  ·  the labelled trace corpus                  |
   the generation archive                                             |
   the validators (spec · process · skill) · the publisher            |
+---------------------------------------------------------------------+
```

"Who improves the improver" is answered structurally, not recursively: a generated policy is data the
kernel interprets, so generation N+1's improver is checked by code that generation N could not edit.

## Mutable — a generation may change these

Each is generated, versioned as a new variant, and gated before it counts.

| Name | What it is in this POC |
| --- | --- |
| `generator-prompts` | the prompts that produce a candidate's agent specs and skill drafts |
| `decomposition` | how the self-description is cut into steps and skills |
| `skill-scripts` | the generated JavaScript under a candidate skill's `scripts/`, run through POC 26's engine |
| `process-shape` | the multi-agent shape: how many agent specs, which hand-offs |
| `detection-policy` | the generated script that decides which recorded failures count as defects |
| `refinement-policy` | the generated policy that decides what to rewrite and from which evidence |
| `improver-pack` | the improver itself, expressed as a pack the runtime executes — the surface that makes the loop self-referential |

One mutation per generation, against a frozen seed prompt. Context collapse is a named risk: a
generation that rewrites everything cannot be attributed to anything.

## Mutable — the builder's own AgentSpec packs

The surface the reframe names is the builder's own packs, four directories under
`prod/embabel-smith-builder/builder-agent-specs/`:

> [Later, as of the current tree: **six** directories, and the folder has moved to
> `prod/embabel-smith-builder/src/main/resources/specs/`. The count and the placement above are as this
> run recorded them; the move is what `docs/12-backlog.md` tracks. What these packs are *on* the
> mutable surface — step `prompt:` text and nothing else — is unchanged.]

| Pack | The process it is the builder of |
| --- | --- |
| `builder-skill-from-process` | builds skills from a process specification |
| `builder-process-composition` | builds AgentSpecs as many agents collaborating |
| `builder-single-spec` | builds one AgentSpec with several steps |
| `builder-spec-one-shot` | drafts one AgentSpec in a single call — and is the pack `smith build --flow pack` runs |

The packs are authored by this change, which also fixes where they sit: a module directory read from the
filesystem, not the published `agent-specs/` spec source and not `src/main/resources/` —
`AgentSpecReader.read(Path)` lists `steps/` and `types/`, and a file packaged into a jar is not
addressable as a directory. Until they land, the mutable surface named here is a path with nothing on it.

> [Later: that placement was reversed. The packs now sit under `src/main/resources/`, and the jar
> addressability problem the sentence above reasons from is solved rather than avoided —
> `smith-common`'s `SpecFolders.classpath` hands back the class directory itself when classes are
> exploded and a temporary copy when they are in a jar. See `docs/12-backlog.md`; the mutable surface
> and its one editable field are unchanged.]

What a generation changes inside a pack is the `prompt:` text of its steps, and nothing else. The
mutation primitive already draws that line in code: `Refiner.withPrompt`
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Refiner.java:157`) rebuilds
an `ActionStepSpec` passing every field through from the step it was given except the sixth, the prompt.

The gates stay fixed for a structural reason, not a policy one. `AgentSpecValidator` records a
`PROMPT_MISSING` finding against any action step whose prompt is absent or blank
(`smith-common/src/main/java/com/embabel/smith/common/validation/AgentSpecValidator.java:216`), so every
step of a pack must carry a prompt — and a deterministic gate, code that decides rather than code that
generates, cannot be expressed inside a pack at all. A mutation limited to pack YAML therefore cannot
*contain* host Java: there is no slot in the format for it to occupy — naming a host service is the next
paragraph, and one such name now resolves. Widening the surface would mean either
accepting prompt-less steps, which voids the validator that makes a pack a pack, or editing Java, which
the fixed `AgentPlatform` entry already refuses because no gate in this repository reviews generated Java
before executing it.

A step can *name* host Java without being host Java: `toolGroups` and the `pre`/`post` condition names
(`smith-common/src/main/java/com/embabel/smith/common/model/ActionStepSpec.java:30`, `:31`, `:36`) refer
to conditions and tool groups supplied from outside the pack. Those fields change a pack's reach, not its
code, and the claim above stands: editing pack text cannot execute host code. What resolution depends on
moved into pack text on 2026-11-22: `builder-single-spec/steps/assembleAgentSpec.yml` and
`builder-spec-one-shot/steps/draftAgentSpec.yml` declare `toolGroups: [validate]`, and
`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ValidationToolGroup.java`
registers that role as a `ToolGroup` bean, so a candidate built from either pack is *offered* the
builder's validator where it was previously offered nothing — `starting tool loop [validatePack]` against
the earlier `starting tool loop []` and its unresolved-role warning. The two measurements of *use* name
their seam: on the test-seam deploy (`AgentSpecInvokerFactory` driven directly) the model declines it,
`called=0` and the goal in one model call; on the builder's `--flow pack` CLI path it calls it. The counts
taken there before 2026-11-22 — 2, 3 and 116 calls across three blueprint inputs — belong to an argument no
drafting step could name, a directory; the tool now takes `specJson`, the spec the step is about to return,
so those counts measure an unaimable call rather than the checks a call runs. The mutation operator copies the field rather than
rebuilding it, so no candidate adds a group: the surface is unchanged, and reach is no longer wholly a
property of code a generation cannot edit. What the surface now also carries is one load-bearing sentence:
since 2026-11-22 `draftAgentSpec.yml` states the reply envelope its goal output type declares, and a
generation that rewrites that step may drop it. Nothing on the fixed side catches that — the validator
never reads prose about output shape, the deploy succeeds, the tier is unchanged — so the break surfaces
only at the read-back. This page's limit to prompt text is still exact; it is no longer a bound.

No operator may change those two fields even so, and the reason is a failure mode rather than a rule.
`AgentSpecValidator` never reads `pre` or `post` and the spec source resolves no condition names, so a
rewritten condition name is not a validation finding: it fails at *deploy*, after the candidate was built,
published and handed to the planner. Silent and expensive, the same class as the misspelled `toolGroups`
entry, with the difference that the prompt-variable case at least acquired a validator rule. Keeping it is
free — `Refiner.withPrompt` copies the field rather than rebuilding it, and the four packs above declare
`pre: []` and `post: []` on all thirteen steps (four packs and thirteen steps as this run counted them;
six packs and eighteen steps now, the two packs added afterwards unchanged in these two fields —
`docs/12-backlog.md`), so there is nothing on the surface to lose.
`prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/eval/CollaborationStructureIsFixedByConstructionTest`
pins both halves on a pack that does name conditions, so the guard is not an absence of evidence.

The same test closes the one structural claim this section does not otherwise reach: an operator cannot
reorder the agents of a process. `ProcessSpec.withAgents` is the only method that changes an order, its sole
main-code caller is `core/ProcessRepair.java:61` on the build path, and nothing under `improve/` imports
`ProcessSpec` at all — `Refiner.refine` is handed a pack, and a pack has no agent order to reorder. So
"the loop improves collaboration structure" is false by signature, not by discipline.

`BuilderAgent` is not mutable, including where a pack mirrors it. `compileFromDescription`
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderAgent.java:91`,
overloads at `:103` and `:116`) is the cognitive stage a pack may restate as prompt-backed steps.
`validateDraft` (`:131`) and `writeAgentSpec` (`:144`) are the same pipeline's gates and remain host
Java: the first calls `AgentSpecValidator.validate`, the second refuses an invalid draft and re-validates
the directory it wrote. A pack may restate the thinking; it may not restate the deciding.

The fixed list below is unchanged by this section. The frozen task set, the labelled corpus and its
held-out split, the generation archive, the noise floor, the self-description, the script-engine limits,
the referee process and human control all apply to a pack mutation exactly as they apply to any other.

## Fixed — a generation may not touch these

| Name | Why it is fixed |
| --- | --- |
| `AgentPlatform` | The Embabel platform and the no-code runtime are the interpreter the claim is relative to. Fix it and "improved" stops meaning anything; there is also no gate in this repository that reviews generated Java before executing it. |
| `EvalScorer` | Called statically inside `GateChain.reeval`, and `docs/10-self-improvement.md` records that seam as not injectable. For this POC that limitation is the referee's tenure, not a defect to fix: an improver that could score itself optimises its scorer, not its work. |
| `GateChain` | Prod code, and `prod/` is not modified by this POC. It is *configured* — its two-argument constructor takes the frozen task list and an invoker factory — which is construction with fixed arguments, not mutation of it. |
| `benchmark-task-set` | `src/main/resources/benchmark/tasks/`. Authored by hand and checked in before any tuning; a candidate that could add a task could choose which work counts, which is the exact hazard this design exists to close. |
| `trace-corpus` | `src/main/resources/corpus/traces/`, including which lines are the held-out split. The detector's judge, so it cannot be the detector's output. |
| `generation-archive` | Every variant, its parent and its score. The archive is what makes archive-versus-greedy comparable; an improver that could edit history could manufacture the derivative. |
| `self-description` | `src/main/resources/self/self_process_description.md`. The seed, written by hand from `docs/05`, `docs/08` and `docs/10` — not extracted, because `smith blueprint` asks for a codebase and a blueprint already is its output. |
| `noise-floor` | `src/main/resources/evidence/noise-floor.json`, measured by repeated scoring before any prompt was tuned. An improvement threshold the loop could move is not a threshold. |
| `script-engine-limits` | POC 26's hardening: no host class lookup, no IO, no thread or process creation, no environment, plus its wall-clock and statement limits. A policy that could widen its own sandbox is not sandboxed. |
| `referee-process` | The separate, hand-started JVM that scores. It loads no skill folder and opens no Graal `Context`, so generated code never runs where it is graded. |
| `human-control` | Approval queue holds, auto-publish off, commit-never-push. Not a knob a generation can turn, and the held-candidate count is a reported number, not a target. |
| `AgentSpecValidator.validate` | `smith-common/src/main/java/com/embabel/smith/common/validation/AgentSpecValidator.java`, `:86` for a pack on disk and `:107` for one in memory. It is the thing that decides whether a mutation is legal, so a candidate that could edit it could pass itself. It is also the first stage of `GateChain.gate` (`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/GateChain.java:115`) and the check behind `BuilderAgent.validateDraft` and `writeAgentSpec`, so every path a candidate travels runs it. |
| `ProcessValidator.validate` | `smith-common/src/main/java/com/embabel/smith/common/validation/ProcessValidator.java:58`. The judge of the composition claim — the hand-offs, goal collisions and cycles between packs in one process — run by `BuilderAgent.writeProcess` (`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderAgent.java:180`), by `BuilderService` (`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java:236`) and by `ProcessRepair` (`:62`). A candidate that could edit it could declare its own hand-offs sound. |
| `SkillMdValidator` | `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillMdValidator.java:31`, called by `SkillBuilderService` (`:186`) before a generated `SKILL.md` is eval'd. It is the published Agent Skills frontmatter constraints in one place, so a skill that could relax them could make itself publishable. |
| `builder/publish/` | `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/`, the whole package. Publishing is where a candidate becomes a durable artifact, so its collision gate (`FolderPublisher.java:46`) and its local-commit-only default — `GitPublisher.java:66` commits, `:72` pushes only when `--push` is set — are what keep a generation's output out of the artifact store. The loop reaches it through `ApprovalQueue` (`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/ApprovalQueue.java:42`, `:89`), whose auto-publish path the `human-control` entry already fixes off. |

## What counts as a mutation

Writing, editing, deleting, or renaming a fixed artifact — including writing it somewhere else and
reading it back. Constructing a fixed class with arguments a generation chose is configuration and is
allowed; that is how the referee is built. Generating a script that *runs* under a fixed engine is
allowed; that is how a policy is expressed.

Generated scripts never reach the referee's execution path. That invariant is enforced by
construction, not intent: the referee process loads no skill folder and opens no script context.
