# Spec Delta

## ADDED Requirements

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
