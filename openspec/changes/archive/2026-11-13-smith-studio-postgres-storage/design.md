# Design

## Context

See proposal.md for motivation. Current state that shapes the approach:

- The studio persists through `ProcessStore` (pack directories +
  `discovery-history.jsonl` under a `store-dir`, default `~/.smith`) and the
  runtime module's `JsonlTraceStore` (`~/.smith/traces/runs.jsonl`). There is
  no datasource, no JPA, and no compose file anywhere in the repo.
- Three full-context `@SpringBootTest` classes must keep booting green without
  docker.
- The app must boot with no storage configured at all (today it boots with no
  model endpoint the same way — placeholder config, actions report
  "not configured").
- The frontend is Vue 3 + Quasar with one API client module and a TypeScript
  type contract; each pane is a routed view.
- The studio module compiles without `-parameters`; controller params use
  explicit `@PathVariable("name")`.

## Goals / Non-Goals

**Goals:**

- JPA storage that rebinds its endpoint at runtime from the UI (hot swap).
- Storage settings persisted locally, editable through a UI pane.
- Packs always in the database; zip download through the UI; import from a
  local filesystem path.
- `TraceStore` SPI with JSONL (default) and Postgres (same or separate
  datasource) backends; record shape unchanged.
- `docker compose` in the studio module for the development database.
- H2 test profile; the rebind path covered by a direct test.

**Non-Goals:**

- `OtelTraceStore`, BigQuery, and MongoDB trace backends (future changes).
- Automatic migration of existing `~/.smith` file data (import covers it).
- Any change to the spec-sources cache, the runtime CLI, or POC modules.

## Decisions

### 1. Hot-swap via a programmatic `StorageContext`, no Spring-managed JPA beans

The user chose JPA with runtime rebind over plain JDBC and over restart-apply.
The consequence: nothing JPA-related may be a context-managed bean, or a
rebind would orphan live beans. So:

- Exclude `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration`
  (and the Spring Data JPA repo auto-config) so the app boots with **no**
  datasource at all — this also solves the "no endpoint configured" boot.
- A single `StorageContext` component owns the lifecycle:

  ```
  volatile StorageBinding current          // null until first bind
  StorageBinding = { HikariDataSource, EntityManagerFactory,
                     PackRepository, DiscoveryRepository, ... }

  bind(settings):
    1. build HikariDataSource from settings
    2. build LocalContainerEntityManagerFactoryBean (programmatic),
       JPA properties: ddl-auto=update, dialect auto-detected
    3. initialize; build repository proxies via JpaRepositoryFactory
    4. verify connectivity (test query); on failure throw -> the existing
       binding stays in effect and the UI shows the connection error
    5. atomically swap `current`
    6. destroy the previous binding (emf.destroy, pool.close)

  @PreDestroy: destroy the current binding
  ```

- `ProcessStore` keeps its exact public API (`savePack`, `listPacks`,
  `loadPack`, `recordDiscovery`, `discoveries`); its internals delegate to
  `StorageContext.current()`. Controllers and services never see a binding.
- A second, independent binding backs the "separate database" trace option
  (same code path, different settings).

Alternatives considered: plain JDBC with a cached `DataSource` per settings
(hot-swap for free, less code — rejected by the user in favor of JPA);
restart-apply of settings (simplest — rejected: the requirement is runtime).

### 2. Pack content as one serialized blob

`packs(name PK, goal, steps, saved_at, content)` where `content` is the
`AgentSpec` serialized as JSON (Jackson; the model is plain records). Load =
parse back to `AgentSpec`. Zip export = write the `AgentSpec` to a temp
directory with the shared `AgentSpecWriter`, zip it, stream the bytes.
`discovery_history(id, ts, source_type, location, doc_count)`.
`traces(...)` mirrors the JSONL record fields.

Alternative considered: one row per step plus a manifest row (queryable, but
reconstructing a deployable directory is ceremony and duplicates structure
the YAML already carries). Rejected.

### 3. Storage settings live in a local document, not the database

Chicken-and-egg: the settings configure the database, so they cannot live in
it. `StorageSettingsService` persists a JSON document at
`~/.smith/studio/storage.json` (mode 0600; directory overridable):

```
{ packDb: { url, user, password },
  trace:  { type: jsonl | postgres-same | postgres-separate,
            url?, user?, password? } }
```

Startup binding: if the document exists, bind to it; else if the property
`smith.studio.storage.url` is set (test profile / env), bind to that; else
stay unbound (store operations report "storage not configured"). REST:
`GET/PUT /api/storage-settings`; `PUT` validates the connection and rebinds.

### 4. `TraceStore` SPI in the runtime module

A `TraceStore` interface mirroring `JsonlTraceStore`'s public API (record a
run, list per spec with a limit) moves next to it; `JsonlTraceStore` becomes
the default implementation, and the CLI's `--trace-dir` keeps selecting it.
The studio's `JpaTraceStore` implements the SPI against the storage binding
(same or separate). The SPI is record-shaped (`TraceRecord` in/out, nothing
storage-specific) so a future `OtelTraceStore` drops in without reworking the
Postgres implementation. `StudioConfiguration` picks the implementation from
the settings document; changing the trace backend at runtime rebinds the
trace store the same way.

### 5. Development database

`prod/embabel-smith-studio/docker-compose.yml`:

- `postgres:16`, `POSTGRES_USER=smith`, `POSTGRES_PASSWORD=smith`,
  `POSTGRES_DB=smith_studio`, port `5432:5432`, named volume `studio-pgdata`,
  `pg_isready` healthcheck.
- Dev workflow: `docker compose up -d`, then point the UI settings at
  `jdbc:postgresql://localhost:5432/smith_studio`.

### 6. Tests on H2

`h2` in test scope. A test-classpath `application.yml` sets
`smith.studio.storage.url` to an in-memory H2 URL, so the three
`@SpringBootTest` classes bind H2 at startup with no docker. A dedicated
`StorageContextTest` binds H2 `mem:one`, writes, rebinds to `mem:two`, and
asserts: new binding serves, old database untouched, old pool closed
(`isClosed()`).

### 7. Frontend

- New routed **Storage** pane: endpoint/user/password fields, trace backend
  picker (JSONL / same database / separate database with its own endpoint),
  save with success/connection-failure feedback.
- Process workspace: a zip download button per saved pack
  (`GET /api/store/packs/{name}/download` → blob download) and an import field
  for a local directory path (`POST /api/store/packs/import`).
- New DTOs mirrored in `frontend/src/types.ts`; API client gains the
  settings get/set, download, and import calls. `vue-tsc --noEmit` stays the
  contract check.

## Risks / Trade-offs

- [Rebind while a transaction is in flight] the old EMF is destroyed after
  the swap; a request mid-transaction on the old binding can fail → dev tool,
  short transactions, the failure surfaces as a clear error; acceptable,
  documented in the pane.
- [H2/Postgres DDL drift] two dialects from one entity model → portable
  column types only (TEXT, BIGINT, TIMESTAMP), `ddl-auto=update` on both; the
  H2 test suite is the drift detector.
- [Credentials in a local file] the settings document stores the password in
  plaintext at `~/.smith/studio/storage.json` → mode 0600, local dev tool on
  the user's own machine, no network exposure; documented.
- [Auto-config exclusion breaking other beans] excluding the datasource/JPA
  auto-configs could hide a bean the platform expects → `ModelNotConfiguredTest`
  (full-context boot) is the guard; run it before any further work.
- [Interaction with the unarchived `smith-studio-process-workspace` delta]
  its "Persist generated processes and discovery history" requirement
  describes the store surface; this change moves its backend to the database
  → archive that change first, then review the merged `studio-web` spec at
  this change's archive for wording conflicts.
- [Hikari pools accumulating on repeated rebinds] each rebind creates a pool;
  the old one is closed in the same call → bounded; the `StorageContextTest`
  asserts the close.

## Migration Plan

1. Compose file + dependencies land first (nothing else changes; reactor
   green, app boots unbound exactly as today).
2. Storage context + settings + `ProcessStore` rewrite + REST + UI.
3. Trace SPI + Postgres backend + UI picker.
4. Rollback: revert the code; the compose file is inert and the old file
   store code is restored with the revert. Existing file-based packs can be
   imported into the database at any time.

## Open Questions

None — the remaining unknowns (UI polish, exact picker labels) do not change
specs, approach, or task breakdown.
