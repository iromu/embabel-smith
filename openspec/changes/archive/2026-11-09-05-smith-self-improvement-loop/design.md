# Design

## The loop

```
                 +------------------------------------------------+
                 |                ImprovementLoop                 |
                 |  (plain class; scheduled or on-demand)         |
                 +------------------------------------------------+
   traces.jsonl |  |
   (change 03)  v  v
              1. DETECT (per skill, rolling window of 200 traces)
                 - failed runs (not tier-blocks)
                 - live re-eval < baseline
                 - content drift (hash != sidecar hash)
                          |
                          v
              2. REFINE  (builder core, change 04)
                 rewrite prompts of failing steps, evidence = traces
                          |
                          v
              3. GATE    (cheap first, expensive last)
                 a. D5 validation (throwaway platform)
                 b. security scan (POC 21)
                 c. re-eval (POC 5)
                 d. pass-rate > baseline ?
                          |
            rejected ----+---- gated
            (recorded)        |
                              v
              4. APPROVE   default: pending queue (evidence attached)
                           auto mode: straight through
                              |
                              v
              5. PUBLISH   (change 04 publishers: folder / git commit)
                           + lifecycle transition (POC 18)
                              |
                              v
              6. WATERMARK  per-skill last-processed-trace recorded
```

## State machine (POC 18, carried into the product)

```
        first proven improvement            regression (re-eval < baseline)
  incubating --------------------------> active --------------------------> degraded
                                            ^                                    |
                                            |                                  | next re-eval fails
                                            | new gated candidate published     v
                                            +-------------------------------- retired
```

- `retired` packs are excluded from automatic refinement (a human revives them via an explicit rebuild in change 06).
- `degraded` packs keep running (the runtime does not gate on lifecycle state in v1 — the state is advisory + loop input). This is a deliberate v1 simplification; a runtime-side gate is a future change.

## Decisions

### Placement: `prod/embabel-smith-studio`, package `...studio.improve`
The loop only exists to serve the combined product, so it lives in studio. But it is plain classes with injected collaborators (`TraceReader`, `BuilderService`, `EvalRunner`, `Publishers`, `LifecycleStore`, `Clock`) — no Spring, no web — so the whole loop is unit-testable offline with fakes, and change 06 just wires it to a scheduler and REST endpoints.

### Gate order: deterministic checks before model calls
D5 validation and the security scan are free and deterministic; the re-eval costs model calls. Ordering them first means a broken or unsafe candidate never spends eval budget. Baseline comparison is last because it needs the re-eval score.

### "Beat the baseline" is strict
`candidatePassRate > baselinePassRate` (strictly). Equal is not progress; accepting equal would let noise churn the source. Consequence: a pack at 1.0 baseline can only improve by fixing *failing* behavior the eval doesn't cover — in practice such packs rarely get queued anyway (no failed traces, no drift).

### Tier-blocked failures are excluded from refinement
A trace with `success=false` caused by the trust-tier gate is a policy outcome, not a skill defect. Refining prompts against it would be noise. The detector filters on the trace's failure reason (change 03 records the tier and the block).

### Approval queue: in-memory, evidence-rich
v1 keeps the pending queue in memory (a `CopyOnWriteArrayList` of `PendingCandidate`: pack, candidate diff summary, traces, scores, gate results). Restart loses the queue, but the traces on disk let the loop rebuild it on the next iteration — an accepted v1 limitation, documented. File-backed persistence is a future change if the UI needs it.

### Watermarks and idempotency
Per-skill watermarks (last processed trace `runId`/timestamp) live in the same in-memory store; "one active improvement per skill" is a `Set<String>` of in-flight skills checked at detect time. Both reset on restart — safe, because reprocessing is idempotent (detection is a pure function of the window + current pack state).

### Retention
The reader takes the last N lines per skill (default 200, configurable). The writer (change 03) stays append-only; retention is a read-side concern so the trace file remains a simple audit log.

## Testability

- Offline (fakes): detection (failed vs tier-blocked vs drift), refine-only-failing-steps, gate order + rejection paths, approval/auto/reject flows, lifecycle transitions (all three edges), watermark skip, in-flight skip, windowing.
- Endpoint-gated (IT, skipped without env): one full loop iteration on a live pack — real refine + real re-eval + publish to a local folder source.
