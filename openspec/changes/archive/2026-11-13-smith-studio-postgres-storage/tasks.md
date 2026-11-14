# Tasks

## 1. Dev infrastructure and dependencies

- [x] 1.1 Add `prod/embabel-smith-studio/docker-compose.yml`: postgres:16, named volume `studio-pgdata`, dev credentials (smith/smith, db `smith_studio`), port 5432, `pg_isready` healthcheck
- [x] 1.2 Add studio pom dependencies: `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` (runtime), `com.h2database:h2` (test)
- [x] 1.3 Exclude the datasource/JPA/Spring-Data-JPA auto-configurations so the app boots with no datasource; verify `ModelNotConfiguredTest` still boots green

## 2. Storage core

- [x] 2.1 Add JPA entities and repositories: `packs(name PK, goal, steps, saved_at, content JSON)`, `discovery_history`, `traces` (portable column types only)
- [x] 2.2 Add `StorageSettingsService`: JSON settings document at `~/.smith/studio/storage.json` (mode 0600, dir overridable); startup resolution (document → `smith.studio.storage.url` property → unbound)
- [x] 2.3 Add `StorageContext`: programmatic binding (HikariDataSource + `LocalContainerEntityManagerFactoryBean` + `JpaRepositoryFactory`), connectivity verify before swap, atomic swap, destroy of the previous binding, `@PreDestroy`; second independent binding for the separate-database trace option
- [x] 2.4 Rewrite `ProcessStore` internals to delegate to the current binding, keeping its exact public API; unbound state reports "storage not configured"
- [x] 2.5 Add storage settings REST: `GET /api/storage-settings` and `PUT /api/storage-settings` (validates connection, rebinds on success, keeps the old binding on failure)
- [x] 2.6 Add `StorageContextTest`: bind H2 `mem:one`, write, rebind to `mem:two`; assert the new binding serves, the old database is untouched, and the old pool is closed

## 3. Pack export and import

- [x] 3.1 Add `GET /api/store/packs/{name}/download`: materialize the stored pack to a temp directory with the shared `AgentSpecWriter`, return it as a zip; unknown pack → clear error
- [x] 3.2 Add `POST /api/store/packs/import` (body: filesystem path): read a valid agent-spec directory into the database; invalid path → clear error, nothing stored

## 4. Pluggable trace storage

- [x] 4.1 Add a `TraceStore` SPI in the runtime module mirroring `JsonlTraceStore`'s public API; make `JsonlTraceStore` the default implementation; CLI `--trace-dir` unchanged
- [x] 4.2 Add `JpaTraceStore` in the studio implementing the SPI against the storage binding (same or separate database)
- [x] 4.3 Wire `StudioConfiguration` to select the trace backend from the settings (jsonl / postgres-same / postgres-separate); changing the backend at runtime rebinds the trace store

## 5. Frontend

- [x] 5.1 Extend `frontend/src/api/types.ts` and the API client: storage settings get/set, pack zip download, pack import
- [x] 5.2 Add the routed Storage pane: endpoint/user/password fields, trace backend picker, save with success/connection-failure feedback
- [x] 5.3 Process workspace: zip download button per saved pack and an import field for a local directory path
- [x] 5.4 Frontend: vitest suite green and `vue-tsc --noEmit` clean; rebuild the bundle into `src/main/resources/static/`

## 6. Test profile and verification

- [x] 6.1 Add a test-classpath `application.yml` pointing `smith.studio.storage.url` at in-memory H2; the three `@SpringBootTest` classes pass with no docker
- [x] 6.2 Full reactor `mvn clean test` green (all modules)
- [x] 6.3 Live end-to-end with compose: `docker compose up -d`, configure storage via `PUT /api/storage-settings`, save a pack, list/reload it, download and unzip the zip, import a local pack dir, switch the trace backend to the same database and confirm a run trace lands there
- [x] 6.4 Update the `.wiki` studio pages to document the storage settings pane, the compose workflow, and the pluggable trace backends

## Workflow follow-up

- Archive the independent `smith-studio-process-workspace` change (complete, unarchived) before archiving this one, then review the merged `studio-web` spec for wording conflicts between its "Persist generated processes and discovery history" requirement and this change's database-backed storage.
