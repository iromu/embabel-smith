# Spec Delta

## ADDED Requirements

### Requirement: Configure storage from the UI
The studio SHALL provide a storage settings pane where the user sets the
database endpoint, user, and password, selects the trace backend (JSONL file,
same database, or a separate database with its own endpoint), and saves.
Saving SHALL report success or the connection failure, and the new settings
SHALL take effect at runtime.

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
The studio SHALL let the user download a saved pack as a zip archive from the
UI, producing a file the user can keep, share, or point a spec source at.

#### Scenario: Download from the UI
- **WHEN** the user triggers the zip download for a saved pack
- **THEN** the browser downloads a zip containing the pack in its standard on-disk agent-spec layout

#### Scenario: Import a local pack directory
- **WHEN** the user gives the path of a local agent-spec directory to import
- **THEN** the pack is stored in the database and appears in the saved pack listing

## MODIFIED Requirements

### Requirement: REST surface
The studio SHALL expose a JSON REST API covering: run (source, goal, input, planner, trust tier), build (description, output source), sources and packs listing, traces listing (per spec), pending candidates listing, candidate approve/reject, loop start/stop/trigger, and auto-publish get/set, plus storage settings get/set, saved-pack zip download, and saved-pack import from a filesystem path.

#### Scenario: API contract is stable for the frontend
- **WHEN** the frontend calls any endpoint
- **THEN** the request/response DTOs match the TypeScript types in `frontend/src/types.ts` (checked by `vue-tsc --noEmit`)

#### Scenario: Endpoint-independent surface
- **WHEN** the studio starts without a model endpoint configured
- **THEN** the app boots, the UI is reachable, and model-backed actions (run, build, loop iterations that re-evaluate) report a clear "model not configured" error rather than crashing
