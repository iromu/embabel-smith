# Tasks

## 1. Loop core (plain classes in `prod/embabel-smith-studio`, package `...studio.improve`)

- [ ] 1.1 Define the loop's models: `PendingCandidate`, `GateResult`, per-skill `Watermark`, `InFlightSet`
- [ ] 1.2 Implement `TraceWindowReader` (rolling window of last N traces per skill over change-03's JSONL store, corrupt-line tolerant)
- [ ] 1.3 Implement `Detector` (failed runs minus tier-blocks; content drift vs lifecycle sidecar hash; re-eval < baseline hook)
- [ ] 1.4 Implement `Refiner` (build candidate via builder core: rewrite only failing steps' prompts, evidence from traces)
- [ ] 1.5 Implement `GateChain` (D5 validation → security scan → re-eval → baseline comparison; cheap-first ordering; rejection recorded with reasons)
- [ ] 1.6 Implement `LifecycleTransitions` (incubating→active, active→degraded, degraded→retired per design state machine; sidecar update)
- [ ] 1.7 Implement `ApprovalQueue` (default hold, approve, reject, auto-publish mode)
- [ ] 1.8 Implement `ImprovementLoop.runOnce()` (detect → refine → gate → approve → publish via change-04 publishers → watermark)

## 2. Offline tests

- [ ] 2.1 Detector tests: failed run queues, tier-block does not queue, drift queues, clean window does not
- [ ] 2.2 Refiner tests: only failing step's prompt changes; evidence (input/output excerpt/reason) is in the rewrite input
- [ ] 2.3 Gate tests: pass path; equal-score rejection; security rejection; validation rejection; gate order (no eval spend on unsafe candidate)
- [ ] 2.4 Lifecycle tests: all three transition edges; retired excludes from auto-refinement
- [ ] 2.5 Approval tests: default hold + approve publishes + reject discards; auto-publish path
- [ ] 2.6 Loop tests: watermark skips processed traces; in-flight skill skipped; window bounds input at N

## 3. Verify + docs

- [ ] 3.1 `mvn -pl prod/embabel-smith-studio test` green offline
- [ ] 3.2 Endpoint-gated integration test (skipped without `OPENAI_CUSTOM_*`): one full loop iteration on a live pack publishes an improved pack to a local folder source
- [ ] 3.3 Write the loop section of `prod/embabel-smith-studio/README.md` (flow diagram, gate order, approval semantics, v1 limitations: in-memory queue, advisory lifecycle state)
