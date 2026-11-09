# Proposal

## Why

Deliverable 2's core: the runtime+builder that **self-improves with the traces**. The runtime (change 03) records every run; the builder (change 04) can produce and gate better packs. What's missing is the loop that watches the traces, detects that a skill is failing or drifting, refines it from its own failure evidence, proves the improvement with a re-eval, and publishes the result back into the source the runtime reads — closing the circle. This change builds that loop as plain, offline-testable logic inside `prod/embabel-smith-studio` (skeleton from change 01); the web surface around it is change 06.

## What Changes

- Fill the loop logic in `prod/embabel-smith-studio` (package `...studio.improve`):
  - **Detection**: from the trace store — failed runs, runs blocked by trust tier (never "fixable" by refinement), and content drift (pack content hash ≠ lifecycle sidecar hash, POC 18).
  - **Refinement**: build a candidate pack by rewriting the failing steps' prompts from the failure traces (POC 9 pattern), using the builder core (change 04).
  - **Re-eval + gate**: score the candidate with the eval harness (POC 5); the candidate must beat the pack's baseline pass-rate, pass validation (D5), and pass the security scan (POC 21) before it may be published.
  - **Publish**: write the improved pack back into the source (folder or git commit, reusing change 04's publishers) and transition the lifecycle state (POC 18: `incubating → active`, regression → `degraded`, sustained regression → `retired`).
  - **Approval policy**: candidates queue as *pending* by default (human approves via the UI in change 06); an explicit auto-publish mode publishes gated candidates directly.
  - **Scheduling**: the loop runs on demand and on a fixed interval; at most one active improvement per skill (idempotent, no concurrent duplicate work).
  - **Retention**: the loop reads a rolling window of the most recent traces per skill (default 200) rather than the full history.
- The loop logic is plain classes — no web, no Spring scheduling required to test it; model calls (refine, re-eval) are endpoint-gated.

## Capabilities

### New Capabilities

- `self-improvement`: the trace-driven improvement loop — detection triggers, candidate refinement, the re-eval + gate policy, publication with lifecycle transitions, the approval/auto-publish policy, scheduling idempotency, and trace retention.

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-studio/` (loop package, tests, README section).
- Depends on `embabel-smith-runtime` (trace store), `embabel-smith-builder` (builder core + publishers), `embabel-smith-skillsource` (change 01 skeleton POM).
- No POC module is modified.
