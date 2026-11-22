# The Self-Improvement Loop

A trace-driven loop whose engine lives in `prod/embabel-smith-builder` (`builder/improve`) and whose
scheduler and wiring live in `prod/embabel-smith-studio` (`studio/loop`): detect specs that are failing
or drifting, refine them from the failure evidence, gate the candidate, and publish it with a lifecycle
transition. The engine moved out of the studio into the builder on 2026-11-21 — building agents is the
builder's concern and the studio is a frontend — so `studio/improve` no longer exists. The evidence it
consumes is described in [`07-runtime.md`](07-runtime.md).

The classes are plain objects with injected collaborators (`TraceWindowReader`, `Detector`,
`Refiner` over a `PromptRewriter`, `GateChain` over an `InvokerFactory` and the eval tasks, a
`Publisher`) — no Spring and no web layer — so the whole loop is unit
testable offline with fakes. Two seams are *not* injectable: the eval scorer is constructed inside
`GateChain`, and time is not a collaborator — `Instant.now()` is inline in `LifecycleTransitions`
and `ImprovementLoop`, so sidecar timestamps are not pinned by tests.

## Detect → refine → gate → publish

**Detection** (`Detector`) triggers on three things: failed traces that are *not* tier blocks, a
re-evaluated pass-rate below the recorded baseline, and content-hash drift. Tier-blocked failures are
excluded on purpose — refining prompts against a policy outcome is noise, and the detector filters on
the failure reason recorded in the trace.

**Refinement** (`Refiner`) rewrites only the steps implicated by the traces, using the trace evidence
as input. It does not regenerate the whole pack. The improvement object is therefore exactly the step
prompts of a pack: `Refiner.withPrompt` rebuilds an `ActionStepSpec` passing every field through from
the step it was given except the sixth, the prompt, so the surface is drawn by that signature rather
than by a list in a document. Two things are consequently off it, and both are worth knowing before
either is claimed. A process's **agent order** is unreachable because the operator is handed a pack and
a pack has no agent order to reorder — `ProcessSpec.withAgents` has one main-code caller,
`core/ProcessRepair` on the build path, and nothing under `improve/` imports `ProcessSpec`. A step's
`pre`/`post` condition names are unreachable too, and the reason is a failure mode rather than a rule:
the spec validator never inspects either field and the spec source resolves no conditions, so an edited
condition name surfaces at **deploy**, after the candidate was built and published. Reach is not off the
surface in the same way. A step's `toolGroups` entry names a role the platform resolves against the
registered `ToolGroup` beans, and `builder/tools/ValidationToolGroup` registers the `validate` role the
builder's packs declare, so what a pack's agent is *offered* is decided partly in pack text.

POC 27 points that same operator at a new object: one of the builder's own four packs under
`prod/embabel-smith-builder/builder-agent-specs/`, whose step prompts are a transcription of the
instructions the builder's generators read off the classpath. That makes the builder's flow an
improvement target without widening what a mutation may touch, and it changes nothing about the shipped
build: the default flow still calls the Java generators, so no candidate of that POC alters what an
ordinary `smith build` does. What did change is reach. Two of those packs declare
`toolGroups: [validate]` — `builder-single-spec/steps/assembleAgentSpec.yml` and
`builder-spec-one-shot/steps/draftAgentSpec.yml` — and `builder/tools/ValidationToolGroup` now registers
that role as a `ToolGroup` bean, so a candidate built from either is *offered* the builder's validator
where it was offered nothing. Measured on the test-seam deploy: `starting tool loop [validatePack]`, with
`called=0` and the goal completed in one model call. Measured on the pack-driven CLI path (`--flow pack`,
not the default): the step calls it. What a call was worth turned on the tool's argument, and `99f9cc0`
changed that argument from a directory a drafting step has no way to name to `specJson` — the spec the step
is about to return — checked by the same in-memory `AgentSpecValidator.validate(AgentSpec)` overload the Java
flow's `core/BuilderAgent.validateDraft` runs. So the counts taken before it (2, 3 and 116 calls across three
inputs, 92 of 93 results on one build the same `LAYOUT_MISSING_MANIFEST`) are what an unaimable argument
produced, not what the tool does now: on one `parcel-claims` pack-flow build since the change the step called
it twice, each time with its own draft, once returning a real `DANGLING_TYPE_REF` and once `No findings: the
agent spec is well-formed.`, and the build published — the other two inputs are being re-measured. The same
two commits also put an output-envelope sentence into
`builder-spec-one-shot/steps/draftAgentSpec.yml` (`e5e3b04`), which is load-bearing text on the one surface a
generation may legitimately rewrite, and no gate reads prose about output shape.
[`12-backlog.md`](12-backlog.md) carries that gap and the matching one in the other three builder packs,
latent only because `--flow pack` deploys `builder-spec-one-shot` alone. The improvement surface is still the
step prompts, and no candidate changes its own reach: `Refiner.withPrompt` copies the other
twelve components, so a candidate's reach is its parent pack's. What moved is that reach is decided partly in
pack text rather than wholly by the driver, so the statement that a candidate cannot change what the builder
can do is no longer true as written
([`04-poc-run-results.md`](04-poc-run-results.md) §7.5 for what that experiment measured;
[`08-builder.md`](08-builder.md) is where these packs belong and does not document them yet).

**The gate** (`GateChain`) runs checks in a deliberate order: **cheap deterministic checks before
model calls.** Structural validation and the static security scan are free; the re-evaluation costs
model calls, so a broken or unsafe candidate never spends eval budget. The baseline comparison comes
last because it needs the re-eval score.

**"Beat the baseline" is strict (`>`).** Equal is not progress, and accepting equal would let noise
churn the source. The stated consequence is worth knowing: a pack already at 1.0 can only improve by
fixing behaviour the eval does not cover, and in practice never gets queued at all — no failed
traces, no drift. A documented failure-fix fallback covers that ceiling case.

**Publication** reuses the builder's publishers and moves the lifecycle state through the POC 18
state machine (`incubating → active → degraded → retired`) via `LifecycleTransitions`. `retired`
is excluded from automatic refinement: a human revives it with an explicit rebuild.

**The loop obeys the commit-never-push rule; pushing is configuration.** The documented invariant is
*commit, never push* without an explicit `--push` (see [`05-architecture.md`](05-architecture.md) and
[`06-spec-sources.md`](06-spec-sources.md)), and the loop now matches it: `smith.studio.loop.push`
decides whether a published candidate is pushed and **defaults to off**, so an unattended loop cannot
write to a shared remote. Unlike auto-publish, it is *not* runtime-toggleable — pushing is the
credential-and-intent decision, so it can only be set when the studio is configured and started.

A `git:` improve source with pushing off is **refused rather than published**. `GitPublisher` commits
inside a temporary clone that `close()` deletes, so a commit made there would vanish silently; the
publish is therefore not attempted. The refusal names `smith.studio.loop.push`, the candidate stays in
the `ApprovalQueue`, and an auto-publishing iteration records that it published nothing
(`IterationInfo.errorCode` = `PUSH_DISABLED`). A `folder:` source is unaffected — a local write is
already durable and the folder publisher ignores the flag. Both write paths
(`ImprovementLoop.publishCandidate`, `LoopService.publish`) share the one `LoopPublishPolicy`. The
loop still passes the collision flag, so its destination directory is replaced on every publish — the
directory is named by the pack alone, so clearing the flag would make the second publish collide
rather than improve. The improvement survives elsewhere: a publish that succeeds also records the
candidate in the studio store, whose `packs` table is keyed by `(name, version)`, and the candidate
carries one patch version above the pack it improved (`improve/Refiner`). Reading a pack back by bare
name therefore returns the improved one, because a bare name means its highest version
([`06-spec-sources.md`](06-spec-sources.md) §Publishing).

**Retirement is enforced; the other states are advisory.** The runtime refuses to ingest a pack
whose sidecar says `retired`, and a run asking for that pack's goal is a named lifecycle block the
detector filters out exactly as it filters a tier block. `INCUBATING` and `DEGRADED` remain advisory
by choice — gating them would be a policy nobody has written down. See
[`07-runtime.md`](07-runtime.md) §Lifecycle state.

## Human control

Candidates **hold for approval by default**. `ApprovalQueue` holds `PendingCandidate` records;
`ImproveController` exposes approve, reject, and an explicit auto-publish mode. A rejected candidate
is never published.

## Scheduling and idempotency

`LoopScheduler` is a `@Scheduled` task on `smith.studio.loop.interval` (default 60 s) that only acts
when `LoopSchedulerState` says the interval is running — **off by default**, started and stopped from
the UI. An on-demand trigger calls `runOnce()` directly and does not touch the schedule.

Two properties make repeated runs safe:

- A **watermark** advances over the trace window, so an iteration does not re-read history.
- **One active improvement per spec**, via `InFlightSet`, checked at detect time.

**Trace retention is a read-side concern.** `TraceWindowReader` applies a rolling window of the most
recent traces per spec, default 200 (`DEFAULT_WINDOW`), and skips corrupt lines with a warning. The
writer itself does no retention at all.

## Accepted v1 limitations

The approval queue and the watermarks are **in-memory**: a restart loses them. That is tolerable
because the on-disk traces let the loop rebuild the queue on the next iteration, and detection is a
pure function of the window plus the pack's current state, so reprocessing is idempotent. A
file-backed queue is a future change if the UI needs durability across restarts.
