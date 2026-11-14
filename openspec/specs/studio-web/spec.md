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
The studio SHALL expose a JSON REST API covering: run (source, goal, input, planner, trust tier), build (description, output source), sources and packs listing, traces listing (per spec), pending candidates listing, candidate approve/reject, loop start/stop/trigger, and auto-publish get/set, plus storage settings get/set, saved-pack zip download, and saved-pack import from a filesystem path.

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

### Requirement: Configure storage from the UI
The studio SHALL provide a storage settings pane where the user sets the database endpoint, user, and password, selects the trace backend (JSONL file, same database, or a separate database with its own endpoint), and saves. Saving SHALL report success or the connection failure, and the new settings SHALL take effect at runtime.

#### Scenario: Configure and save storage settings
- **WHEN** the user fills in a reachable database endpoint and saves
- **THEN** the pane reports success and subsequent store operations use that database

#### Scenario: Connection failure surfaced
- **WHEN** the user saves settings pointing at an unreachable database
- **THEN** the pane shows the connection failure and the previous working configuration remains in effect

#### Scenario: Trace backend picker
- **WHEN** the user selects a trace backend other than the JSONL file
- **THEN** subsequent run traces are stored in the selected backend and the Traces pane reads them from it

### Requirement: Download a saved pack as a zip
The studio SHALL let the user download a saved pack as a zip archive from the UI, producing a file the user can keep, share, or point a spec source at.

#### Scenario: Download from the UI
- **WHEN** the user triggers the zip download for a saved pack
- **THEN** the browser downloads a zip containing the pack in its standard on-disk agent-spec layout

#### Scenario: Import a local pack directory
- **WHEN** the user gives the path of a local agent-spec directory to import
- **THEN** the pack is stored in the database and appears in the saved pack listing

### Requirement: Author a process from a blueprint
The studio SHALL let the user describe a business process — as free text, a local docs
directory, or a repository URL — and generate a GOAP skill-pack (one exported goal, action steps,
and intermediate types) by running the generation prompt against the model. The model proposes the
step decomposition; the response carries the pack and any validation issues.

#### Scenario: Generate from docs
- **WHEN** the user supplies a docs path and requests generation
- **THEN** the model returns a pack whose goal is reachable (its output type is produced by some
  step) and whose steps are wired by type, and the UI renders it

#### Scenario: Generation reports issues
- **WHEN** the generated pack fails a structural check (e.g. a dangling input type)
- **THEN** the response lists the issues and the UI shows them alongside the pack

#### Scenario: No model endpoint
- **WHEN** generation is requested without a configured model endpoint
- **THEN** the endpoint returns the structured `MODEL_NOT_CONFIGURED` error and the UI shows a
  "no endpoint" state rather than crashing

### Requirement: Discover business-process docs
The studio SHALL let the user point at a local docs directory or a repository URL and load the
business-process documents for review, so the user sees what will feed generation. A repository URL
is cloned to a local copy first. An auto/manual toggle controls whether generation runs
immediately after the docs are loaded.

#### Scenario: Load a docs directory
- **WHEN** the user supplies an existing docs path and loads it
- **THEN** the documents are listed in the UI for review

#### Scenario: Load from a repository URL
- **WHEN** the user supplies a repository URL and loads it
- **THEN** the repository is cloned to a local copy and its business-process docs are listed

#### Scenario: Auto-generate after discovery
- **WHEN** the auto toggle is enabled and the docs load successfully
- **THEN** generation starts without a further user action

### Requirement: Render the process as an editable flow
The studio SHALL render the generated pack as a directional flow graph in which parallel steps share
a column, dependent steps advance to the next column, and the goal is rightmost; the user SHALL be
able to edit any step or the goal and have the graph reflect the edit.

#### Scenario: Layered layout
- **WHEN** a pack with parallel analyst steps is rendered
- **THEN** the parallel steps fan out in the first column and the downstream chain leads to the goal
  at the far right

#### Scenario: Edit a step
- **WHEN** the user edits a step's prompt, inputs, or output type
- **THEN** the graph and the published/simulated pack reflect the edit

### Requirement: Simulate a generated process
The studio SHALL let the user dry-run the current pack with a sample input and show the per-step
trace and the goal output, without publishing anything.

#### Scenario: Simulate a run
- **WHEN** the user submits a sample input and simulates
- **THEN** the run executes through the runtime core and the UI shows each step's status and
  duration plus the goal output (or the failure reason)

#### Scenario: Simulate is throwaway
- **WHEN** a simulation completes
- **THEN** no pack is published and the throwaway source is cleaned up

### Requirement: Publish a generated process
The studio SHALL let the user publish the current pack to a skill source so the runtime can run it
as a deployed goal.

#### Scenario: Publish
- **WHEN** the user picks a source and publishes
- **THEN** the pack is written to that source and appears as an exported goal

### Requirement: Generated prompt variables satisfy the runtime contract
The generator SHALL normalise each action step's prompt variables to the runtime's decapitalized
input-type-name contract (input type `TechnicalReport` is referenced as `{{technicalReport}}`), so
a generated pack passes structural validation and is discoverable by the folder skill source.

#### Scenario: Snake-case variables are canonicalised
- **WHEN** the model emits `{{technical_report}}` for an input type `TechnicalReport`
- **THEN** the generated step's prompt references `{{technicalReport}}` and the pack validates

#### Scenario: Unrecognised variables are preserved
- **WHEN** a prompt references a variable that matches no declared input type
- **THEN** it is left unchanged so the pack still fails validation with a clear issue

### Requirement: Extract a business process from the docs
The studio SHALL let the user run the model against the loaded business-process documents to
extract a business-process description (a blueprint) that can feed generation. Extraction reuses
the model seam and the discovery prompt, and is endpoint-gated. The extracted description becomes
the generation blueprint.

#### Scenario: Extract from loaded docs
- **WHEN** the user requests extraction from a docs path or a repository URL
- **THEN** the model returns a business-process description and the UI shows it as the blueprint
  that generation will use

#### Scenario: No model endpoint
- **WHEN** extraction is requested without a configured model endpoint
- **THEN** the endpoint returns the structured `MODEL_NOT_CONFIGURED` error and the UI shows a
  "no endpoint" state rather than crashing

### Requirement: Persist generated processes and discovery history
The studio SHALL persist generated skill-packs and discovery history durably across runs, so
previously generated packs can be listed and reloaded and prior discoveries can be reviewed.

#### Scenario: Save a generated pack
- **WHEN** the user saves the current pack
- **THEN** it is written to the durable store and appears in the saved-packs list

#### Scenario: Reload a saved pack
- **WHEN** the user loads a saved pack
- **THEN** the flow is rendered from the persisted pack, identical to what was saved

#### Scenario: Review discovery history
- **WHEN** the user reviews discovery history
- **THEN** prior discoveries (source type, location, document count, time) are listed
