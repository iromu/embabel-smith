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
either is claimed. An agents-pack's **agent order** is unreachable because the operator is handed a pack and
a pack has no agent order to reorder — `AgentsPack.withAgents` has one main-code caller,
`core/AgentsPackRepair` on the build path, and nothing under `improve/` imports `AgentsPack`. A step's
`pre`/`post` condition names are unreachable too, and the reason is a failure mode rather than a rule:
the spec validator never inspects either field and the spec source resolves no conditions, so an edited
condition name surfaces at **deploy**, after the candidate was built and published. Reach is not off the
surface in the same way. A step's `toolGroups` entry names a role the platform resolves against the
registered `ToolGroup` beans, and `builder/tools/ValidationToolGroup` registers the `validate` role the
builder's packs declare, so what a pack's agent is *offered* is decided partly in pack text. Since the
agentic change that holds for four roles across four of the six packs: `builder-agentic-blueprint`'s two
steps and `builder-harness`'s three declare `blueprint`, `library`, `validate` and `security`, and
`builder-single-spec`'s `assembleAgentSpec` and `builder-spec-one-shot`'s `draftAgentSpec` each declare
`validate` alone — seven steps across those four packs, thirteen declarations, four distinct roles, every
one of them a published `ToolGroup` bean listed by the booted platform's `availableToolGroups()`. The other
eleven steps carry an explicit empty list rather than omitting the key, so `grep -c toolGroups:` over the
steps directory counts all eighteen step files and the four packs above are what a
`grep -A1 'toolGroups:'` returns with something after it. And a declaration naming
a role the platform cannot resolve is now refused at the agentic deploy
(`eval/AgenticAgentSpecGenerator#refuse`) rather than deploying verbatim and resolving to nothing only at
prompt time.

POC 27 points that same operator at a new object: one of the builder's own six packs under
`prod/embabel-smith-builder/src/main/resources/specs/`, whose step prompts are a transcription of the
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
[`12-backlog.md`](12-backlog.md) carries that gap and the matching one in the other builder packs, latent
only because each pack flow deploys one pack alone — `--flow pack` `builder-spec-one-shot`,
`--flow agentic` `builder-agentic-blueprint`, `--flow harness` `builder-harness` — and each of those three
carries the sentence. The improvement surface is still the step prompts, and no candidate changes its own
reach: `Refiner.withPrompt` copies the other
twelve components, so a candidate's reach is its parent pack's. What moved is that reach is decided partly in
pack text rather than wholly by the driver, so the statement that a candidate cannot change what the builder
can do is no longer true as written
([`04-poc-run-results.md`](04-poc-run-results.md) §7.5 for what that experiment measured;
[`08-builder.md`](08-builder.md) now documents these packs and the four flows that run them).

**The script-authoring half of `generate_skill.jinja` is now mutable text.** That prompt gained the
instructions deciding whether a published skill ships `scripts/*.js` — when to emit one, the globals it may
use, and the no-network/no-host promise it must be written against — so the prose that chooses what code a
reader ends up running now sits on the same surface as the other generation prompts, and a candidate can
rewrite it while leaving the sentences perfectly fluent. What did *not* become mutable is the scorer: the
three scoring prompts `grade_eval` / `agent_baseline` / `agent_with_skill` stay classpath resources under
`skill/ScorerPromptBoundaryTest`, which is the settled wording of
[`08-builder.md`](08-builder.md) §Who issues the generation call rather than a claim restated here, and the
test is the line rather than a convention. Two things keep the new mutability narrow in practice: the
in-JVM engine is a bean behind `smith.builder.skill-scripts.enabled`, off by default, so an ordinary build
offers no script tool at all; and the two `Skills` that can hold it are both behind that same switch —
the build-internal one `skill/SkillScriptHost` writes for the skill *under build*, and the reader-side one
`skill/PublishedSkillHost` builds from the folder `publish` wrote, which `smith skill-run` is the only
route that attaches. So a candidate that starts emitting scripts changes what a published folder
*contains*, and with the switch on what the with-skill eval arm and that one route can execute, while an
ordinary build and every other reader stay instruction-only
([`08-builder.md`](08-builder.md) §`skill` for the target, [`12-backlog.md`](12-backlog.md) for the
readers still open). The comparability caveat that follows from the switch is the loop's,
not this page's: a lift measured with the switch on is not comparable with one measured with it off.

**The agentic flow changes who runs the checks, not the mutable surface.** `--flow agentic` deploys
`builder-agentic-blueprint` through `eval/AgenticAgentSpecGenerator` instead of a Java generator, and it
differs from `java` and `pack` in one respect: the deterministic checks run in Java *between* model calls —
the driver validates and scans each draft itself and re-invokes the same step with the findings appended, at
most twice — because on one measured run the drafting step was *offered* `validatePack` and declined it, and
eval then found sixteen dangling type references. `--flow harness` inherits that forcing rather than
restating it — `eval/HarnessAgentSpecGenerator` extends the same driver and swaps the pack it deploys — so
what follows holds for it too, with `builder-harness` as its parent pack. Three things follow for anyone who
points the loop at that pack. The reachable set is unchanged, for the `Refiner.withPrompt` reason above: a
candidate of this pack is
offered exactly the four roles its parent pack declares. A pass-rate comparison across flows is not
like-for-like on model-call cost, because the agentic route structurally spends up to two extra invocations
per build on top of the tool calls the step itself makes. And the pack's prompts name tools and their
arguments — `validatePack` and `scan_spec` take `specJson`, set to the document the step is about to return —
which is text a rewrite can break while leaving the prose perfectly fluent, and no gate reads it either.
Whether the route builds anything at all is no longer UNPROVEN: the agentic arm has since run against the
endpoint and built **0 of 3** against the 2-of-3 bar, twice on `parcel-claims` with identical numbers —
survey answered in 40 s, then 41 draft-call timeouts until the 2700 s cutoff — with its steps making 124 to
179 tool calls rather than declining them. So the tool-use half is answered yes and the built-count half
no, on this endpoint, and the reason is throughput rather than routing: a whole AgentSpec does not fit
inside the 60 s interaction clock once the step has spent part of it reading
([`08-builder.md`](08-builder.md) §Who issues the generation call, and task 5.5 of the agentic change for
the per-arm numbers). The registry assertions in that change still say nothing about model behaviour.

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

Five alternatives to that refusal were considered and rejected, and they constrain the next change
here rather than being open questions: keeping the forced push and documenting the loop as exempt —
rejected because it keeps the worst property of the status quo, that the only person who can stop a
write to a shared remote is whoever edits Java; removing pushing from the loop entirely — rejected
because an operator who deliberately points the loop at a `git:` source and accepts pushing would have
no way to say so; publishing anyway and reporting `pushed: false` — rejected because that field reads
as "committed locally, not yet pushed", which for this publisher means *committed nowhere*, a report
that is true and useless; keeping the clone alive so the commit survives — rejected because it invents
a durable publish state machine to serve a configuration that should simply be refused; and deciding
git-ness by `publisher instanceof GitPublisher` — rejected because it couples loop policy to a bean
type the loop has no reason to know, so the URI is parsed with `PublishTarget.parse` instead.

**Retirement is enforced; the other states are advisory.** The runtime refuses to ingest a pack
whose sidecar says `retired`, and a run asking for that pack's goal is a named lifecycle block the
detector filters out exactly as it filters a tier block. `INCUBATING` and `DEGRADED` remain advisory
by choice — gating them would be a policy nobody has written down. See
[`07-runtime.md`](07-runtime.md) §Lifecycle state.

## Before a paid run means anything

Five preconditions decide whether a measured improvement means anything. Each is checked *before* the
spend, never explained after it. They are stated here as durable rules because the product's loop
shares them with the experiment that earned them: POC 27's outer loop burned a wall clock's worth of
calls and produced no scored candidate at all, and the cause of that — a candidate prompt that asked
the same question of every task — was invisible to every test in the tree
([`04-poc-run-results.md`](04-poc-run-results.md) §7.5 holds the numbers; the rules are here).

- **Headroom is proven first.** A paid run is refused, with the score that caused the refusal and no
  call made, when the unmodified subject already passes every task in the frozen set: a saturated
  benchmark yields a flat derivative whatever the loop tries. The check must also have been *able* to
  say no — a headroom check that has only ever seen failures is not proven, so an all-correct answer
  set is run through it.
- **Judge disagreement removes its own authority.** The deciding deterministic arm and an independent
  human-labelled arm are both recorded per task; a task where they disagree is excluded from the
  deciding score, reported by identifier, and the smaller denominator is stated with it. Agreement is
  then a count in the report rather than an assumption. On POC 27's six-task set the two arms disagreed
  on two of them, which is why the gate arm's 0.667 and the human arm's 0.333 are both printed.
- **The noise floor precedes the derivative.** Repeated scoring of an *unchanged* subject sets the
  floor, and it is reported at its measured value: POC 27 measured 0.0 over 3 repeats and 18 calls
  with no task flipping, and passed it through as zero rather than raising it for safety. A candidate
  delta inside the floor is not an improvement.
- **Distinct tasks must produce distinct answers.** If two tasks with different inputs received an
  identical answer, the run stops with a named refusal and a recorded reason before the rest of its
  budget goes. The check is proven able to pass — a varying stand-in clears it, a constant-returning
  one trips it — because a guard nobody showed firing is decoration. The format now refuses the pack
  shape that hid it: a step declaring inputs whose prompt interpolates none of them is invalid
  (`PROMPT_IGNORES_DECLARED_INPUT`, see
  [`agent-spec-format.md`](agent-spec-format.md) §Validation).
- **Spend is declared as arithmetic, on two axes.** The call ceiling is derived from the task count and
  the declared generations, the reason for the spend is stated beside the number, and a *wall-clock*
  ceiling applies as well as the call ceiling — a call budget alone can still take hours. Ten tasks
  declaring 110 calls at the measured per-call latency is refused against the 5400 s ceiling before the
  first call, which is why POC 27's headroom tasks live beside the frozen six rather than inside them:
  the frozen file count is an input to the arithmetic.

**Recursion is measured across generations, and every generation is kept.** A claim that the improver
improved itself means nothing from a single generation: what is reported is a per-generation derivative,
with the frozen task set untouched throughout and *every* generation retained — including variants that
scored below their parent, because an archive that keeps only winners is a greedy search wearing a
recursion costume. POC 27's inner loop converged after 4 of 12 generations on its own seed, every
mutation scoring worse, which is exactly why its verdict reads *not proven* rather than proven. A
detection policy is judged the same way: offline, against labelled traces, reporting precision and
recall together rather than whichever of the two a change improved.

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
