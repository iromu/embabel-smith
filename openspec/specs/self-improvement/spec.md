# self-improvement Specification

## Purpose
The trace-driven self-improvement loop: watch the runtime's traces, detect skills that are failing or drifting, refine them from their own failure evidence, prove the improvement with a re-evaluation and the standard gates, and publish the improved pack back into the skill source with a lifecycle transition. The loop is what makes the runtime+builder product improve itself.

## Requirements

### Requirement: Detection triggers
The loop SHALL consider a skill for improvement when any of: (a) its recent traces contain failed runs whose failure is not a trust-tier block; (b) a live re-evaluation scores below the pack's baseline pass-rate; (c) the pack's content hash differs from the hash recorded in its lifecycle sidecar (content drift).

#### Scenario: Failed runs trigger refinement
- **WHEN** a skill's recent traces contain failed runs with step-level failures
- **THEN** the loop queues that skill for improvement

#### Scenario: Tier blocks are not fixable
- **WHEN** a skill's failed runs failed solely because a step was blocked by the trust tier
- **THEN** the loop does NOT queue a refinement for them (refinement cannot change tier policy)

#### Scenario: Content drift triggers re-evaluation
- **WHEN** a pack's current content hash differs from its lifecycle sidecar's recorded hash
- **THEN** the loop re-evaluates the pack against its baseline before deciding anything

### Requirement: Candidate refinement from traces
When a skill is queued, the loop SHALL build a candidate pack by rewriting the prompts of the steps implicated in the failures, using the failure traces as evidence (POC 9 pattern), and SHALL leave steps without failure evidence unchanged.

#### Scenario: Only failing steps change
- **WHEN** a candidate is built from traces where only step `s2` failed
- **THEN** the candidate differs from the current pack only in `s2`'s prompt (and lifecycle metadata)

#### Scenario: Refinement uses the failure evidence
- **WHEN** the refiner rewrites a failing step's prompt
- **THEN** the rewrite input includes the failed step's input, output excerpt, and failure reason from the trace

### Requirement: Candidate must beat the baseline
A candidate SHALL be eligible for publication only if its re-evaluation pass-rate is strictly greater than the pack's current baseline pass-rate, and it passes validation (D5) and the security scan (no high-severity findings).

**Ceiling fallback.** A builder-built pack is stamped at its build-time score, which the build's score gate pins to the maximum achievable on the (binary) smoke task — a baseline the strict re-eval can never beat. When a candidate is already at the ceiling on its own eval tasks (re-eval at the maximum pass-rate) yet still cannot beat a ceiling baseline, and it was refined from concrete production failures, the gate instead proves the actual improvement: it re-runs the candidate on those failing inputs and requires a successful (non-blank) output for each. The strict baseline-beat remains the primary criterion; the fallback applies only in this ceiling case and never to a drift-only pass or to a candidate that is failing its own eval tasks.

#### Scenario: Better candidate passes the gate
- **WHEN** a candidate's re-eval beats the baseline and it validates and scans clean
- **THEN** it becomes a publishable candidate

#### Scenario: Ceiling baseline with a fixed failure passes
- **WHEN** a candidate is at the ceiling on its eval tasks, cannot beat the ceiling baseline, and produces a successful output on every triggering failure
- **THEN** it becomes a publishable candidate (the actual improvement is proven)

#### Scenario: Ceiling baseline with an unfixed failure is rejected
- **WHEN** a candidate is at the ceiling on its eval tasks but still produces no output on a triggering failure
- **THEN** it is rejected with the unfixed inputs recorded, and the current pack is untouched

#### Scenario: Equal-or-worse candidate is rejected
- **WHEN** a candidate's re-eval does not beat the baseline (and the ceiling fallback does not apply)
- **THEN** it is rejected with the scores recorded, and the current pack is untouched

#### Scenario: Security failure rejects
- **WHEN** a candidate passes the score gate but has a high-severity scan finding
- **THEN** it is rejected and the finding is recorded

### Requirement: Publication updates the source and lifecycle
On publication (approved or auto), the loop SHALL write the candidate pack into the skill source (reusing the builder's folder/git publishers) and SHALL update the lifecycle sidecar: new content hash, new baseline pass-rate, and the state transition per the POC 18 state machine (`incubating → active` on first proven improvement; `active → degraded` on regression; `degraded → retired` on sustained regression).

#### Scenario: Improvement publishes and activates
- **WHEN** an `incubating` pack's gated candidate is published
- **THEN** the source contains the new pack content and the sidecar shows the new hash, new baseline, and state `active`

#### Scenario: Regression degrades
- **WHEN** a live re-evaluation of an `active` pack scores below its baseline
- **THEN** its state moves to `degraded` and it is queued for refinement

#### Scenario: Sustained regression retires
- **WHEN** a `degraded` pack fails its next re-evaluation again
- **THEN** its state moves to `retired` and it is no longer queued for automatic refinement

### Requirement: Approval policy
By default, gated candidates SHALL be held in a pending queue requiring explicit approval before publication; an explicit auto-publish mode SHALL publish gated candidates without approval. Rejected candidates SHALL never be published.

#### Scenario: Default holds for approval
- **WHEN** a candidate passes all gates and auto-publish is off
- **THEN** it is held in the pending queue, visible with its evidence (traces, scores, diff summary), until approved or rejected

#### Scenario: Approval publishes
- **WHEN** a pending candidate is approved
- **THEN** it is published and the lifecycle transitions

#### Scenario: Auto-publish
- **WHEN** auto-publish is on and a candidate passes all gates
- **THEN** it is published without a pending step

#### Scenario: Rejection
- **WHEN** a pending candidate is rejected
- **THEN** it is discarded with the rejection recorded; the current pack is untouched

### Requirement: Loop scheduling is idempotent
The loop SHALL run on demand and on a fixed interval, SHALL track a per-skill watermark (last processed trace), and SHALL have at most one active improvement in flight per skill.

#### Scenario: No duplicate work
- **WHEN** the loop fires while an improvement for skill `s` is still in flight
- **THEN** it skips `s` for this iteration

#### Scenario: Watermark avoids reprocessing
- **WHEN** the loop completes an iteration
- **THEN** traces already processed are not reprocessed in the next iteration

### Requirement: Trace retention
The loop SHALL operate on a rolling window of the most recent traces per skill (default 200), not the full history.

#### Scenario: Window bounds the input
- **WHEN** a skill has more traces than the window size
- **THEN** only the most recent window's worth inform its improvement decision
