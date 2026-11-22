---
title: "The smith studio's durable storage: Postgres, hot-swap, and pluggable traces"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageContext.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageBinding.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/PacksKeyMigration.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/entity/PackEntity.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageSettings.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/JpaTraceStore.java"
  - "smith-common/src/main/java/com/embabel/smith/common/trace/TraceStore.java"
  - "smith-common/src/main/java/com/embabel/smith/common/trace/TraceRecord.java"
  - "prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/trace/JsonlTraceStore.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/store/ProcessStore.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StorageSettingsController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StoreController.java"
  - "prod/embabel-smith-studio/frontend/src/panes/StoragePane.vue"
  - "docker/docker-compose-infra.yml"
updated_at: "2026-11-22"
---

# The smith studio's durable storage

The studio ([[smith-studio]], [[smith-studio-web]]) used to keep saved packs, the discovery
history, and run traces as local files. This change moves them to a **Postgres database** that the
user points at from the UI — while the app still **boots and stays usable with no database at all**.
Everything JPA-related is built **programmatically** and **hot-swapped** on rebind; nothing is a
Spring-managed bean, so a rebind never orphans live beans.

## Booting with no database

`SmithStudioApplication` excludes the datasource and Spring-Data-JPA auto-configurations, so no
`DataSource` / `EntityManagerFactory` / repository bean is ever created by Spring. Startup
resolution, in order: the persisted settings document → the `smith.studio.storage.url` property (the
test profile / env) → **unbound**. A configured-but-unreachable database at startup leaves the
context unbound (a warning) rather than failing the boot. When unbound, listing operations return
empty and mutations/loads report a clear **"storage not configured"** error
(`StorageNotConfiguredException`).

## The hot-swappable binding

`StorageContext` owns the bindings and the trace backend. A `StorageBinding` is one database: a
Hikari pool, a programmatically built `LocalContainerEntityManagerFactoryBean` (scanning the entity
package, `hbm2ddl.auto=update`), the repository proxies (via `JpaRepositoryFactory` on a shared
`EntityManager`), and a `TransactionTemplate` — every repository call runs inside it, because
non-Spring-managed repositories have no `@Transactional` interceptor. `StorageBinding.create()`
runs a `SELECT 1` **before** anything is returned; if the endpoint is unreachable it tears down the
partially built resources and throws, so the caller keeps its previous binding.

Rebind is an **atomic swap**: the new binding is verified, swapped into the `volatile` field, and
the previous binding is destroyed (pool closed, EMF destroyed). `@PreDestroy` closes everything on
shutdown. There is a **second, independent binding** for the separate-database trace option; a
separate trace DB that is unreachable keeps the previous trace backend rather than failing the main
rebind.

**Entities** (portable column types only — long text is `SqlTypes.LONGVARCHAR`, *not* `@Lob`, which
would map to Postgres' non-portable `oid` large-object type): `packs` (**`(name, version)` primary
key**, so every version of a pack is its own row; denormalized goal/steps/saved_at, content = the
`AgentSpec` as JSON; a bare name read back means that pack's highest version, compared in Java rather
than in a query — see [[smith-studio]] and `docs/06-spec-sources.md`), `discovery_history`, and
`traces` (the scalar fields of a run trace; per-step records as a JSON array in `steps_json`).

`hbm2ddl.auto=update` adds tables and columns but never re-keys a primary key, so a studio deployed
before the composite key would come up with a name-keyed table under an entity expecting two key
columns. `storage/PacksKeyMigration` runs at bind time, **before** the entity-manager factory is
built: it reads the key through JDBC metadata and, when it is not `(name, version)`, adds the column,
backfills each row from the version its own stored `content` declares (default `1.0.0`), and replaces
the key. Rows are never dropped.

## The Storage pane and settings

`GET /api/storage-settings` returns the persisted document (or the defaults); `PUT` accepts the
pane's flat form (endpoint/user/password + trace backend), **validates the connection, rebinds live
on success, and persists the settings** — on an unreachable endpoint it returns the failure and the
previous working binding stays in effect. The routed **Storage pane** (`/storage`) pre-fills from
the stored settings and shows success or the connection-failure message. The settings live as a
local JSON document at `~/.smith/studio/storage.json` (mode 0600) — they cannot live in the
database they configure.

## The pack store API

`StoreController` is the whole durable-pack surface, and all six routes go through `ProcessStore`:

- `POST /api/store/packs` — save an assembled pack; `GET /api/store/packs` — list what is stored
  (name + goal + step summary); `GET /api/store/packs/{name}` — load one back as JSON.
- `GET /api/store/packs/{name}/download` — materialize the stored pack with the shared
  `AgentSpecWriter` and return it as a **zip**.
- `POST /api/store/packs/import` (body: a filesystem path) — read a valid agent-spec directory into
  the database.
- `GET /api/store/discoveries` — the discovery history.

Saved packs live in the database; the filesystem is only an exchange format. Download and import
surface clear errors (unknown pack; not a valid agent-spec directory) and store nothing on failure.
The Process workspace ([[studio-process-workspace]]) exposes a per-pack **Download** button and an
**import** field.

## Pluggable trace backends

The `TraceStore` SPI (`append` / `read`) and its `TraceRecord` live in `smith-common`
(`common/trace/`), not in the runtime module; the implementations stayed there — `JsonlTraceStore` is
the default and the CLI `--trace-dir` is unchanged. The studio adds `JpaTraceStore` against a storage
binding (same or separate database). `StorageContext` exposes a **delegating** `TraceStore` that
forwards to the current backend, so the backend can switch **at runtime** — `jsonl` (default),
`postgres-same` (the packs' database), or `postgres-separate` (an independent database) — without the
record shape changing.

## Development database and test profile

`docker/docker-compose-infra.yml` (repo-level, the only compose file the studio uses) brings up a
`postgres:16` with a named volume (`studio-pgdata`) and fixed dev credentials —
`embabel_smith`/`embabel_smith`, database `embabel_smith`, host port **5433** → container 5432, with
a `pg_isready` healthcheck. It is the documented endpoint the Storage pane points at (5433 so it
coexists with a Postgres already on the conventional 5432). *The compose file's own header comment
still names the old `smith`/`smith_studio` credentials — read the `environment:` block, not the
comment.*

A gitignored `application-local.yaml` at `prod/embabel-smith-studio/src/main/resources/` supplies the
dev settings: it sets `smith.studio.storage.url` to that endpoint and turns on
`spring.docker.compose`, so a plain dev run auto-starts the container and binds to it. It is reached
via `spring.profiles.default: local` in `application.yml`, which applies **only when no profile is
set** — an explicit profile skips it, so a packaged run never spawns the container. Note that the
file is gitignored by a bare `application-local.yaml` pattern (it would be ignored anywhere), and a
stray copy still lands in `target/classes` on a build. The test-classpath `application.yml` keeps
docker-compose off and points `smith.studio.storage.url` at in-memory H2, so the `@SpringBootTest`
classes pass with no docker.

## A web-server gotcha worth knowing

The transitive `embabel-agent-eval` jar ships `spring.main.web-application-type=none` (it is a
headless CLI tool). Because Spring Boot 4 re-binds `spring.main.*` from the environment, that file
can shadow the module's `application.yml`, and the app would silently boot with **no HTTP server**.
`main()` therefore sets `spring.main.web-application-type=SERVLET` as a **system property** (higher
precedence than any classpath config file) — immune to the jar shadowing.

**Tests:** `StorageContextTest` (bind → write → rebind; the new binding serves, the old database is
untouched, the old pool is closed; unbound reports "storage not configured"; an unreachable endpoint
keeps the previous binding), `ProcessStoreTest` (save/list/load/discovery against a bound H2), the
`TraceControllerTest` (per-spec window over the store), and the endpoint-gated `RestIntegrationTest`
+ `LiveLoopIntegrationTest` (full app boots and serves with the H2 test profile).
