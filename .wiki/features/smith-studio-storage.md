---
title: "The smith studio's durable storage: Postgres, hot-swap, and pluggable traces"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageContext.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageBinding.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/StorageSettings.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/storage/JpaTraceStore.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/store/ProcessStore.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StorageSettingsController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StoreController.java"
  - "prod/embabel-smith-studio/frontend/src/panes/StoragePane.vue"
  - "prod/embabel-smith-studio/docker-compose.yml"
updated_at: "2026-11-12"
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
would map to Postgres' non-portable `oid` large-object type): `packs` (name PK, denormalized
goal/steps/saved_at, content = the `AgentSpec` as JSON), `discovery_history`, and `traces` (the
scalar fields of a run trace; per-step records as a JSON array in `steps_json`).

## The Storage pane and settings

`GET /api/storage-settings` returns the persisted document (or the defaults); `PUT` accepts the
pane's flat form (endpoint/user/password + trace backend), **validates the connection, rebinds live
on success, and persists the settings** — on an unreachable endpoint it returns the failure and the
previous working binding stays in effect. The routed **Storage pane** (`/storage`) pre-fills from
the stored settings and shows success or the connection-failure message. The settings live as a
local JSON document at `~/.smith/studio/storage.json` (mode 0600) — they cannot live in the
database they configure.

## Pack export and import

Saved packs are always in the database; the filesystem is only an exchange format.
`GET /api/store/packs/{name}/download` materializes the stored pack with the shared
`AgentSpecWriter` and returns it as a **zip**; `POST /api/store/packs/import` (body: a filesystem
path) reads a valid agent-spec directory into the database. Both surface clear errors (unknown pack;
not a valid agent-spec directory) and store nothing on failure. The Process workspace exposes a
per-pack **Download** button and an **import** field.

## Pluggable trace backends

The runtime module defines a `TraceStore` SPI (`append` / `read`); `JsonlTraceStore` is the default
and the CLI `--trace-dir` is unchanged. The studio adds `JpaTraceStore` against a storage binding
(same or separate database). `StorageContext` exposes a **delegating** `TraceStore` that forwards to
the current backend, so the backend can switch **at runtime** — `jsonl` (default), `postgres-same`
(the packs' database), or `postgres-separate` (an independent database) — without the record shape
changing.

## Development database and test profile

`docker-compose.yml` brings up a `postgres:16` with a named volume (`studio-pgdata`) and fixed dev
credentials (smith/smith, db `smith_studio`, host port 5433 → container 5432, `pg_isready`
healthcheck) — the documented endpoint the Storage pane points at (5433 so it coexists with a
Postgres already on the conventional 5432). A gitignored `application-local.yaml` (module root,
activated by the default `local` profile) sets `smith.studio.storage.url` to that endpoint and turns
on `spring.docker.compose`, so a plain dev run auto-starts the container and binds to it; the
test-classpath `application.yml` keeps docker-compose off and points `smith.studio.storage.url` at
in-memory H2, so the `@SpringBootTest` classes pass with no docker.

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
