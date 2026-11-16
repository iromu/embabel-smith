# The Self-Improvement Loop

A trace-driven loop in `prod/embabel-smith-studio` (`studio/improve`, `studio/loop`): detect specs
that are failing or drifting, refine them from the failure evidence, gate the candidate, and publish
it with a lifecycle transition. The evidence it consumes is described in
[`07-runtime.md`](07-runtime.md).

The classes are plain objects with injected collaborators (`TraceWindowReader`, `BuilderService`,
`EvalScorer`, the publishers, a clock) — no Spring and no web layer — so the whole loop is unit
testable offline with fakes.

## Detect → refine → gate → publish

**Detection** (`Detector`) triggers on three things: failed traces that are *not* tier blocks, a
re-evaluated pass-rate below the recorded baseline, and content-hash drift. Tier-blocked failures are
excluded on purpose — refining prompts against a policy outcome is noise, and the detector filters on
the failure reason recorded in the trace.

**Refinement** (`Refiner`) rewrites only the steps implicated by the traces, using the trace evidence
as input. It does not regenerate the whole pack.

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

**Lifecycle state is advisory in v1 — the runtime does not gate on it.** That is a deliberate
simplification with the runtime-side gate left as a separate change.

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
