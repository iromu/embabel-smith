---
title: "The smith studio: the self-improvement loop"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/ImprovementLoop.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Detector.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Refiner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/GateChain.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/LifecycleTransitions.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/ApprovalQueue.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/TraceWindowReader.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/LoopPublishPolicy.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopService.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopScheduler.java"
  - "prod/embabel-smith-studio/README.md"
updated_at: "2026-11-22"
---

# The smith studio: the self-improvement loop

**Where the code lives changed on 2026-11-21.** The loop's engine — `ImprovementLoop`, `Detector`,
`Refiner`, `GateChain`, `ApprovalQueue`, `LifecycleTransitions`, `TraceWindowReader` — moved from
`prod/embabel-smith-studio` into `prod/embabel-smith-builder` (`builder/improve`), because building
agents is the builder's concern and the studio is a frontend. What the studio keeps is the driving:
`studio/loop/LoopService` and `LoopScheduler`, the REST surface, and the Vue UI. Everything this page
says about **what the loop does** still holds; read "the studio" below as "the loop", and see
[[smith-builder]] for its new home.

The loop (change 05) watches
the runtime's traces ([[smith-runtime]]) and, when a spec's recent runs show trouble, rewrites the
failing steps' prompts and republishes the pack — but only when a candidate **provably beats** the
current pack's baseline. It now shares the builder's eval, security, and publish machinery
([[smith-builder]]) as code in the same module rather than as a dependency across modules. The loop
itself is plain Java; change 06 exposes it as a web product — a REST surface, a Vue UI, and an
interval scheduler — see [[smith-studio-web]].

## The pass (`ImprovementLoop.runOnce()`)

For each pack in the source, in one pass:

1. **Read the window** — the most recent N traces per spec (default 200; a rolling window, not full
   history). Skip the spec if the window is empty or it is already caught up to its **watermark**.
2. **Detect** (`Detector`) — any of three triggers queues the spec:
   - failed runs — but **not** trust-tier blocks (a blocked run is policy, not a spec defect);
   - a live re-eval scoring below the pack's baseline (regression);
   - content drift (the pack's hash no longer matches its `lifecycle.yml` sidecar).
3. **Refine** (`Refiner`) — rewrite only the *failing* steps' prompts, using the failure traces
   (input, output excerpt, failure reason) as the rewrite evidence. Steps with no failure evidence
   are untouched; if nothing changed there is no candidate.
4. **Gate** (`GateChain`) — cheap-first: D5 validation ([[validate-before-write]]) → security scan
   ([[spec-security-scanning]]) → re-eval ([[eval-scoring]]) → **strict** beat-the-baseline
   (`score > baseline`; equal is not progress). A broken or unsafe candidate spends no eval budget.
   **Ceiling fallback:** a builder-built pack is stamped at the build's max score on the (binary)
   smoke task — a baseline the strict re-eval can never beat. When a candidate is already at the
   ceiling on its own eval tasks yet still cannot beat a ceiling baseline, and it was refined from
   concrete production failures, the gate instead proves the actual improvement: it re-runs the
   candidate on those failing inputs and requires an output that is non-blank *and different from
   the recorded failure* (reproducing the same output excerpt is not a fix). The strict
   baseline-beat remains the primary criterion; the fallback never applies to a drift-only pass or
   to a candidate failing its own eval tasks.
5. **Publish + lifecycle** — a gated candidate is published through the builder's folder/git
   publishers and the sidecar is updated ([[spec-lifecycle]]): `incubating → active` on the first
   proven improvement, `active → degraded` on regression, `degraded → retired` on sustained
   regression. **Retired packs are excluded** from automatic refinement. A publish against a `git:`
   source is refused unless `smith.studio.loop.push` is enabled
   (`builder/improve/LoopPublishPolicy.java:45`): the commit would live only in the publisher's
   temporary clone and be deleted with it, so the candidate is held in the queue instead.

## Approval

By default a gated candidate **holds** in an in-memory queue (`ApprovalQueue`) with its evidence
visible; an explicit auto-publish publishes immediately. Rejected candidates are never published,
and the most recent rejection (with its score) is recorded per spec. The queue is thread-safe (the
scheduler tick and the REST threads can touch it at once), and `approve()` publishes before
removing, so a failed publish leaves the candidate pending rather than silently losing it.

## Idempotency

Two guards keep a re-run cheap: a **per-spec watermark** (last processed trace) and **at most one
in-flight improvement per spec**, checked before any re-eval spend. A re-run over unchanged traces
publishes nothing.

## v1 limitations

In-memory queue and watermarks (a restart reprocesses the window from the start); lifecycle state
advisory **except for retirement** — the runtime drops a pack its sidecar marks `retired` at ingest
(`runtime/core/RuntimeCore.java:113`) and refuses a goal only a retired pack exported
(`LifecycleBlockedException`, same package) — while its trust tiers stay binding for every pack; and
no per-spec error isolation (a failure in one spec aborts the pass — that spec retries on the next
run). Scheduling is provided by the web layer ([[smith-studio-web]]). The model endpoint is
env-driven as in the POCs ([[model-endpoint]]).

**Tests:** `DetectorTest`, `RefinerTest`, `GateChainTest`, `LifecycleTransitionsTest`,
`ApprovalQueueTest`, `TraceWindowReaderTest`, `ImprovementLoopTest` (offline) and the
endpoint-gated `LiveLoopIntegrationTest`.
