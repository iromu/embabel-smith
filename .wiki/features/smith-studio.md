---
title: "The smith studio: the self-improvement loop"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/ImprovementLoop.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/Detector.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/Refiner.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/GateChain.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/LifecycleTransitions.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/ApprovalQueue.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/TraceWindowReader.java"
  - "prod/embabel-smith-studio/README.md"
updated_at: "2026-11-08"
---

# The smith studio: the self-improvement loop

`prod/embabel-smith-studio` hosts the product's **self-improvement loop** (change 05): it watches
the runtime's traces ([[smith-runtime]]) and, when a skill's recent runs show trouble, rewrites the
failing steps' prompts and republishes the pack — but only when a candidate **provably beats** the
current pack's baseline. It reuses the builder's eval, security, and publish machinery
([[smith-builder]]) rather than reimplementing it. The loop itself is plain Java; change 06 exposes
it as a web product — a REST surface, a Vue UI, and an interval scheduler — see
[[smith-studio-web]].

## The pass (`ImprovementLoop.runOnce()`)

For each pack in the source, in one pass:

1. **Read the window** — the most recent N traces per skill (default 200; a rolling window, not full
   history). Skip the skill if the window is empty or it is already caught up to its **watermark**.
2. **Detect** ([[Detector]]) — any of three triggers queues the skill:
   - failed runs — but **not** trust-tier blocks (a blocked run is policy, not a skill defect);
   - a live re-eval scoring below the pack's baseline (regression);
   - content drift (the pack's hash no longer matches its `lifecycle.yml` sidecar).
3. **Refine** ([[Refiner]]) — rewrite only the *failing* steps' prompts, using the failure traces
   (input, output excerpt, failure reason) as the rewrite evidence. Steps with no failure evidence
   are untouched; if nothing changed there is no candidate.
4. **Gate** ([[GateChain]]) — cheap-first: D5 validation ([[validate-before-write]]) → security scan
   ([[skill-security-scanning]]) → re-eval ([[eval-scoring]]) → **strict** beat-the-baseline
   (`score > baseline`; equal is not progress). A broken or unsafe candidate spends no eval budget.
5. **Publish + lifecycle** — a gated candidate is published through the builder's folder/git
   publishers and the sidecar is updated ([[skill-lifecycle]]): `incubating → active` on the first
   proven improvement, `active → degraded` on regression, `degraded → retired` on sustained
   regression. **Retired packs are excluded** from automatic refinement.

## Approval

By default a gated candidate **holds** in an in-memory queue ([[ApprovalQueue]]) with its evidence
visible; an explicit auto-publish publishes immediately. Rejected candidates are never published,
and the most recent rejection (with its score) is recorded per skill. The queue is thread-safe (the
scheduler tick and the REST threads can touch it at once), and `approve()` publishes before
removing, so a failed publish leaves the candidate pending rather than silently losing it.

## Idempotency

Two guards keep a re-run cheap: a **per-skill watermark** (last processed trace) and **at most one
in-flight improvement per skill**, checked before any re-eval spend. A re-run over unchanged traces
publishes nothing.

## v1 limitations

In-memory queue and watermarks (a restart reprocesses the window from the start); advisory
lifecycle state (only the runtime's trust tiers are binding); and no per-skill error isolation (a
failure in one skill aborts the pass — that skill retries on the next run). Scheduling is provided
by the web layer ([[smith-studio-web]]). The model endpoint is env-driven as in the POCs
([[model-endpoint]]).

**Tests:** `DetectorTest`, `RefinerTest`, `GateChainTest`, `LifecycleTransitionsTest`,
`ApprovalQueueTest`, `TraceWindowReaderTest`, `ImprovementLoopTest` (offline) and the
endpoint-gated `LiveLoopIntegrationTest`.
