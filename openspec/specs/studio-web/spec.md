# studio-web Specification

## Purpose
The unified web product: one application that runs agent specs from a source, builds new agent specs, and operates the trace-driven self-improvement loop — with a UI where each of those is a pane. It is the production counterpart of POC 22, re-pointed at the production cores and extended with trace and loop control.

## Requirements

### Requirement: Run goals from the UI
The studio SHALL let the user pick a spec source, pick an exported goal from that source, submit an input, and see the goal output — executing through the change-03 runtime core.

#### Scenario: Run from the UI
- **WHEN** the user selects a source, a goal, and an input and submits
- **THEN** the run executes and the pane shows the goal output (or the failure reason)

#### Scenario: Run records a trace
- **WHEN** a run completes
- **THEN** the Traces pane subsequently shows that run

### Requirement: Build packs from the UI
The studio SHALL let the user submit a description and an output source, and show the build report (validation, security scan, score, publish location) — executing through the change-04 builder core.

#### Scenario: Build from the UI
- **WHEN** the user submits a description and a valid output source
- **THEN** the pane shows the build report, and the pack appears in the output source

#### Scenario: Blocked build reported
- **WHEN** the build is blocked by validation, the security gate, or the score gate
- **THEN** the pane shows the blocking reason and nothing is published

### Requirement: Operate the improvement loop
The studio SHALL expose loop control: start/stop the interval schedule, trigger a single on-demand iteration, list pending candidates with their evidence (triggering traces, gate results, scores, diff summary), approve or reject a candidate, and toggle auto-publish.

#### Scenario: Start and stop the schedule
- **WHEN** the user starts the loop schedule
- **THEN** iterations run at the configured interval until the user stops it

#### Scenario: On-demand iteration
- **WHEN** the user triggers a single iteration
- **THEN** the loop runs once and the pane reports which specs were detected, refined, gated, and published or queued

#### Scenario: Approve a candidate
- **WHEN** the user approves a pending candidate
- **THEN** it is published and the lifecycle state updates in the pane

#### Scenario: Auto-publish toggle
- **WHEN** the user enables auto-publish
- **THEN** gated candidates are published without pending approval until the user disables it

### Requirement: Show traces
The studio SHALL show, per spec, the rolling window of its trace records: run time, source ref, success/failure, per-step status and durations, and the failure reason where present, with step-level drill-down.

#### Scenario: Trace history
- **WHEN** the user opens the Traces pane for a spec
- **THEN** it lists that spec's recent runs, most recent first, within the retention window

#### Scenario: Failure drill-down
- **WHEN** the user opens a failed run
- **THEN** it shows the per-step records and the failure reason

### Requirement: Show lifecycle state
The studio SHALL show each pack's lifecycle state (`incubating` / `active` / `degraded` / `retired`), its baseline pass-rate, and its content-hash drift status.

#### Scenario: Lifecycle visible
- **WHEN** the user views a pack
- **THEN** its lifecycle state, baseline, and drift status are shown

### Requirement: REST surface
The studio SHALL expose a JSON REST API covering: run (source, goal, input, planner, trust tier), build (description, output source), sources and packs listing, traces listing (per spec), pending candidates listing, candidate approve/reject, loop start/stop/trigger, and auto-publish get/set.

#### Scenario: API contract is stable for the frontend
- **WHEN** the frontend calls any endpoint
- **THEN** the request/response DTOs match the TypeScript types in `frontend/src/types.ts` (checked by `vue-tsc --noEmit`)

#### Scenario: Endpoint-independent surface
- **WHEN** the studio starts without a model endpoint configured
- **THEN** the app boots, the UI is reachable, and model-backed actions (run, build, loop iterations that re-evaluate) report a clear "model not configured" error rather than crashing

### Requirement: Frontend keeps the testable architecture
The frontend SHALL keep POC 22's architecture: pure logic (models, validation, state) separated from the views, a single API client module, and a TypeScript type contract mirroring the DTOs.

#### Scenario: Offline frontend tests pass
- **WHEN** the frontend test suite runs (`vitest`)
- **THEN** all tests pass, and `vue-tsc --noEmit` reports no type errors

### Requirement: Loop scheduling is off by default
The interval schedule SHALL be disabled at startup; the loop runs only when started from the UI or triggered on demand.

#### Scenario: Fresh start is quiet
- **WHEN** the studio starts
- **THEN** no loop iterations run until the user starts the schedule or triggers one
