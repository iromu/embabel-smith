# Spec Delta

## Purpose

The studio's durable storage: saved agent specs and discovery history persisted
in a hot-swappable Postgres database whose endpoint is configurable at runtime
from the UI, with zip export, filesystem import, and pluggable trace storage.

## ADDED Requirements

### Requirement: Packs and discovery history are stored in the database
Saved agent specs and discovery records SHALL be persisted in the configured
database rather than on the local filesystem. Saving, listing, and reloading a
pack, and recording and listing discoveries, SHALL all read from and write to
the database.

#### Scenario: Save a generated pack
- **WHEN** the user saves a generated spec through the store surface
- **THEN** the pack is stored in the database and appears in the pack listing with its name, goal, step count, and save time

#### Scenario: Reload a saved pack
- **WHEN** the user loads a saved pack by name
- **THEN** the full spec content (goal, steps, types, manifest) is returned from the database

#### Scenario: Storage not configured
- **WHEN** the studio starts with no storage endpoint configured
- **THEN** the app boots and the UI is reachable, and store operations report a clear "storage not configured" error rather than crashing

#### Scenario: Record and list discoveries
- **WHEN** a discovery succeeds and the user opens the discovery history
- **THEN** the record (timestamp, source type, location, document count) is stored in the database and listed most recent first

### Requirement: Storage endpoint is configurable at runtime
Storage settings (database endpoint, user, password, trace backend) SHALL be
persisted in a local settings document and changeable at runtime through the
UI. A settings change SHALL rebind storage to the new endpoint without a
restart, and the previous connection SHALL be released.

#### Scenario: Change the endpoint from the UI
- **WHEN** the user saves storage settings pointing at a different database
- **THEN** subsequent store operations use the new database without a restart

#### Scenario: Previous connection released on rebind
- **WHEN** storage rebinds to a new endpoint
- **THEN** the previous connection pool is closed and no further operations use it

#### Scenario: Settings survive a restart
- **WHEN** the studio restarts
- **THEN** it binds to the last saved storage settings

#### Scenario: Unreachable endpoint reported
- **WHEN** the user saves settings pointing at an unreachable database
- **THEN** the UI reports the connection failure and the previous working binding remains in effect

### Requirement: Packs can be exported as a zip
A saved pack SHALL be downloadable as a zip archive containing the pack's
files in the standard on-disk agent-spec layout (manifest plus step files),
materialized on demand from the database content.

#### Scenario: Download a saved pack
- **WHEN** the user requests the zip download of a saved pack
- **THEN** the response is a zip whose extracted directory is a valid agent-spec directory readable by the existing spec ingestion

#### Scenario: Download of unknown pack
- **WHEN** the user requests the zip of a pack that is not stored
- **THEN** the request fails with a clear "no such pack" error

### Requirement: Packs can be imported from the filesystem
An existing agent-spec directory on the local filesystem SHALL be readable and
storable in the database through an import operation.

#### Scenario: Import a pack directory
- **WHEN** the user imports a valid agent-spec directory path
- **THEN** the pack is stored in the database and appears in the pack listing with its full content

#### Scenario: Invalid import source
- **WHEN** the user imports a path that is not a valid agent-spec directory
- **THEN** the import fails with a clear reason and nothing is stored

### Requirement: Trace storage is pluggable
Trace records SHALL be writable to a selectable backend: a JSONL file (the
default), the same database as the packs, or a separate database configured
independently in the storage settings. The trace record shape SHALL be
unchanged by the backend, and the traces surface SHALL read from the selected
backend.

#### Scenario: Default backend is the JSONL file
- **WHEN** the studio runs with no trace backend selected
- **THEN** traces are written to the JSONL file exactly as before this change

#### Scenario: Traces in the same database
- **WHEN** the user selects the same-database trace backend
- **THEN** run traces are stored in the packs' database and the traces surface lists them

#### Scenario: Traces in a separate database
- **WHEN** the user selects a separate-database trace backend with its own endpoint
- **THEN** run traces are stored in that second database while packs remain in the first

### Requirement: Development database via docker compose
The studio module SHALL ship a docker compose file that starts a Postgres
service with a named data volume and documented development credentials,
reachable at a fixed local port, so that `docker compose up` is sufficient to
bring up a development database.

#### Scenario: Start the development database
- **WHEN** the developer runs the studio's compose file
- **THEN** a Postgres is reachable at the documented local endpoint with the documented credentials, and data persists across compose restarts via the named volume

### Requirement: Tests do not require a live Postgres
The studio test suite SHALL run without docker or a live Postgres, using an
in-memory database for all database-backed paths, including the hot-swap
rebind path.

#### Scenario: Full suite green without docker
- **WHEN** the studio module's tests run with no database available
- **THEN** all tests pass, including the full-context boot tests

#### Scenario: Rebind path tested
- **WHEN** the storage context test binds an in-memory database, writes, and rebinds to a second in-memory database
- **THEN** the new binding serves the operations, the old database is untouched, and the old connection is closed
