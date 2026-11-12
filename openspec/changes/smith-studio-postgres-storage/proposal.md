# Proposal

## Why

The studio persists everything as files under `~/.smith` (pack directories,
`discovery-history.jsonl`, `runs.jsonl`) and has no database at all — no
datasource, no compose file, nowhere to point it at real storage. The user
needs Postgres-backed storage for development: a `docker compose` that starts
the database, specs always stored in the database (exported as a zip through
the UI, importable from the filesystem), the database endpoint configurable
at runtime from the UI, and a pluggable trace store that can use the same
Postgres, a different datasource, or stay file-based — with OpenTelemetry,
BigQuery, and MongoDB backends possible later.

## What Changes

- New `docker-compose.yml` in the studio module: a `postgres:16` service with
  a named volume and dev credentials, for local Spring development.
- JPA storage with a **hot-swappable binding**: the storage context owns the
  `DataSource` + `EntityManagerFactory` lifecycle programmatically (no
  Spring-managed JPA beans), so the endpoint can change at runtime from the UI
  and the previous pool/EMF is torn down on rebind.
- Storage settings: a locally persisted settings document (endpoint, user,
  password, trace backend) with a REST surface and a UI settings pane;
  changing it rebinds storage live.
- `ProcessStore` becomes database-backed: saved packs (always in the DB,
  content serialized) and discovery history replace the file store.
- Pack export: a zip download endpoint and a download button in the process
  workspace; import: load an existing pack directory from the filesystem into
  the DB.
- Trace store becomes pluggable: a `TraceStore` SPI in the runtime module,
  `JsonlTraceStore` becomes the default implementation, and the studio adds a
  Postgres implementation selectable from the UI (same Postgres, a separate
  datasource, or JSONL file).
- Tests run against H2 via a test profile; the hot-swap path (rebind to a
  second database, old pool closed) is tested directly.
- Out of scope, named for future changes: `OtelTraceStore`, BigQuery and
  MongoDB trace backends.

## Capabilities

### New Capabilities

- `studio-storage`: the studio's durable storage — packs and discovery
  history in a hot-swappable Postgres, runtime-configurable storage settings,
  zip export, filesystem import, and pluggable trace storage selection.

### Modified Capabilities

- `agent-spec-runtime`: the "Trace store is file-based" requirement becomes
  pluggable — a `TraceStore` SPI with JSONL as the default implementation;
  the record shape and the CLI `--trace-dir` contract are unchanged.
- `studio-web`: new UI surface — a storage settings pane (endpoint,
  credentials, trace backend picker) and a zip download for saved packs —
  plus the matching REST endpoints on the existing JSON contract.

## Impact

- `prod/embabel-smith-studio`: pom (JPA, PostgreSQL driver, H2 for tests),
  `StudioConfiguration`, `store/ProcessStore`, new storage context +
  entities + settings service, new/changed REST controllers, frontend
  (settings pane, download button, API client and types).
- `prod/embabel-smith-runtime`: `JsonlTraceStore` re-shaped behind a
  `TraceStore` SPI (behavior of the existing file store unchanged).
- New `prod/embabel-smith-studio/docker-compose.yml`.
- No POC modules touched; the prod enforcer ban on `poc-*` is unaffected.
