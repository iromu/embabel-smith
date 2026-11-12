# embabel-smith-studio

The self-improving studio product: one Spring Boot web app that runs specs from a source,
builds new specs, and operates the trace-driven self-improvement loop — with a web UI where
each of those is a pane. It hosts the production cores from change 03 (runtime), change 04
(builder), and change 05 (self-improvement loop) behind a REST surface, and serves a Vue 3 +
Quasar SPA built from the same tree.

## Boot

Build the SPA into the app's `static/` directory, then run the app:

```
cd prod/embabel-smith-studio/frontend && npm install && npm run build
cd .. && mvn -pl prod/embabel-smith-studio spring-boot:run
```

The Vite build emits into `src/main/resources/static`, so the running app serves the UI at `/`
and the API under `/api`.

### Local port

The default port is `8080` (`SERVER_PORT`). If that port is held by another local service,
override it. The frontend dev server proxies `/api` to `http://localhost:18080`, so run the app
on `18080` for the `npm run dev` flow:

```
SERVER_PORT=18080 mvn -pl prod/embabel-smith-studio spring-boot:run
```

### Model endpoint (env-driven)

The model endpoint is fully environment-driven — nothing is hardcoded (same convention as the
runtime and builder):

| Variable | Purpose |
|---|---|
| `OPENAI_CUSTOM_BASE_URL` | OpenAI-compatible endpoint (LiteLLM, OpenAI, Ollama, ...) |
| `OPENAI_CUSTOM_API_KEY` | API key |
| `OPENAI_CUSTOM_MODELS` | Comma-separated model list exposed to the platform |

### Model-not-configured behavior

The app boots and the UI is reachable **without** a model endpoint configured. Model-backed
actions (Run, Build, Eval, and loop iterations that re-evaluate) return the structured
`MODEL_NOT_CONFIGURED` error rather than crashing; the model-backed panes render a dedicated
"model not configured" banner for it. Offline actions — listing sources and packs, the security
scan, the trust check, and reading traces — work regardless.

## Configuration

| Property | Env | Default | Purpose |
|---|---|---|---|
| `smith.studio.sources` | `SMITH_STUDIO_SOURCES` | — | Comma-separated source URIs the UI offers (`folder:<path>` or `git:<url>[@ref]`). |
| `smith.studio.improve-source` | `SMITH_STUDIO_IMPROVE_SOURCE` | first of `sources` | The source the loop watches and refines. |
| `smith.studio.trace-dir` | `SMITH_STUDIO_TRACE_DIR` | `~/.smith/traces` | Trace store dir; the store file is `<dir>/runs.jsonl`. |
| `smith.studio.source-cache-dir` | `SMITH_STUDIO_SOURCE_CACHE_DIR` | `~/.smith/spec-sources` | Git source cache root. |
| `smith.studio.loop.interval` | `SMITH_STUDIO_LOOP_INTERVAL` | `60000` | Interval between scheduled iterations (ms); the schedule is **off by default**. |
| `smith.studio.loop.auto-publish` | `SMITH_STUDIO_LOOP_AUTO_PUBLISH` | `false` | Auto-publish gated candidates instead of holding them. |

## Panes

| Pane | Backed by | Notes |
|---|---|---|
| Run | change-03 runtime | Pick a source + goal, submit an input, see the goal output. |
| Build | change-04 builder | Describe a spec and publish it to a source. |
| Eval | change-04 builder | Score a spec over its tasks and refine the prompt from failures. |
| Lifecycle | builder sidecars | Each pack's lifecycle state, baseline, and content-hash drift. |
| Security | install-time scanner | Static scan of a pack; offline. |
| Trust | trust-tier policy | Check a spec's tool groups against a trust tier; offline. |
| Traces | change-03 trace store | A spec's recent runs, most recent first, with per-step drill-down. |
| Auto-improve | change-05 loop | Loop control, pending candidates with evidence, approve/reject, auto-publish. |
| Storage | storage context | Point the studio at its Postgres database and pick the trace backend; save validates and rebinds live. |
| Compose / Retrieve / Scale / Remote | — | POC-only panes, kept for reference and disabled in the product. |

## REST surface

| Endpoint | Purpose |
|---|---|
| `POST /api/run` | Run a goal (source, goal, input, planner, trust tier). |
| `POST /api/build` | Build a pack (description, output source). |
| `POST /api/eval` | Score + refine a spec over its tasks. |
| `GET /api/sources` | List the configured sources. |
| `GET /api/packs?source=` | List a source's packs with lifecycle state + drift. |
| `GET /api/traces?spec=&limit=` | A spec's trace window, most recent first. |
| `POST /api/security` | Static scan of a pack. |
| `POST /api/trust` | Trust-tier check for a pack. |
| `GET /api/improve/candidates` | Pending loop candidates with evidence. |
| `POST /api/improve/candidates/{id}/approve` | Approve + publish a candidate. |
| `POST /api/improve/candidates/{id}/reject` | Reject a candidate. |
| `POST /api/loop/trigger` | Run one on-demand iteration. |
| `POST /api/loop/start` · `stop` | Start / stop the interval schedule. |
| `GET /api/loop/status` | Loop schedule + auto-publish state. |
| `GET` · `PUT` `/api/loop/auto-publish` | Read / set auto-publish. |
| `GET` · `PUT` `/api/storage-settings` | Read / set the database endpoint + trace backend (PUT validates the connection and rebinds live). |
| `GET /api/store/packs` · `GET /api/store/packs/{name}` | List saved packs / load one by name. |
| `POST /api/store/packs` | Save a pack to the database. |
| `GET /api/store/packs/{name}/download` | A saved pack as a zip. |
| `POST /api/store/packs/import` | Import an agent-spec directory from a filesystem path into the database. |
| `GET /api/store/discoveries` | The recorded discovery history. |

The request/response DTOs mirror the TypeScript types in `frontend/src/api/types.ts`;
`vue-tsc --noEmit` catches a field rename on either side.

## Durable storage (Postgres)

Saved packs, the discovery history, and run traces live in a Postgres database the user points at
from the **Storage** pane. The app **boots and stays usable with no database** — the datasource and
JPA auto-configurations are excluded, and storage is bound **programmatically and hot-swapped** on
rebind (nothing JPA-related is a Spring bean, so a rebind never orphans live beans). When unbound,
listing endpoints return empty and writes report "storage not configured".

- **Dev database:** `docker compose up -d` (in this directory) starts a `postgres:16` at
  `jdbc:postgresql://localhost:5432/smith_studio` (user/password `smith`/`smith`, db `smith_studio`,
  named volume `studio-pgdata`). Point the Storage pane at it.
- **Settings** persist as `~/.smith/studio/storage.json` (mode 0600) — they cannot live in the
  database they configure.
- **Trace backends** are pluggable and switchable at runtime: `jsonl` (default, the change-03 file
  store), `postgres-same` (the packs' database), or `postgres-separate` (an independent database).

## Operating the loop

The interval schedule is **off by default**: on a fresh start no iterations run until the user
starts the schedule (Auto-improve pane) or triggers one on demand.

- **Trigger** — one iteration now; the pane reports which specs were detected, refined, gated,
  and published or queued.
- **Start / Stop** — run iterations at the configured interval until stopped.
- **Auto-publish** — when on, gated candidates are published immediately; when off (default)
  they are held in the queue for approval. The loop is always built in hold mode; the toggle is
  applied at the loop-service layer so it takes effect live.
- **Approve / Reject** — approve publishes a held candidate and transitions its lifecycle;
  reject discards it.

## The self-improvement loop

The loop watches the runtime's traces (change 03) and, for each spec in the source, detects
that it is failing or drifting, refines it (change 04's builder core), gates the candidate, and
publishes the approved one back to the source with a lifecycle transition. One pass is
`ImprovementLoop.runOnce()`.

The loop logic is plain Java — no web, no scheduling required to test it. Model calls (refine,
re-eval) are endpoint-gated.

### Flow

```mermaid
flowchart TD
    A[runOnce] --> B[for each spec in the source]
    B --> C{new traces since the watermark?}
    C -- no --> Z[skip the spec]
    C -- yes --> D[DETECT: failed runs / content drift / re-eval below baseline]
    D --> E{queued and not in flight?}
    E -- no --> Z
    E -- yes --> F[REFINE: rewrite the failing steps' prompts from the traces]
    F --> G[GATE: validate, scan, re-eval, beat baseline]
    G --> H{gated?}
    H -- no --> Z
    H -- yes --> I[APPROVE: hold by default, or auto-publish]
    I --> J[PUBLISH: write to the source + lifecycle transition]
    J --> K[WATERMARK: advance to the latest trace]
    K --> Z
```

Per spec:

1. **Detect** — the rolling window of the most recent traces (default 200) is checked for
   failed runs (minus trust-tier blocks, which are a policy outcome, not a spec defect),
   content drift (the pack's content hash differs from the sidecar's recorded hash), and a
   live re-eval below the baseline (regression, only spent for `active`/`degraded` specs).
2. **Refine** — the prompts of the failing steps are rewritten using the failure traces as
   evidence (input, output excerpt, failure reason); steps without failure evidence are
   left unchanged.
3. **Gate** — the candidate must clear every gate (below).
4. **Approve** — the gated candidate is held in the `ApprovalQueue` (default) or auto-published.
5. **Publish** — the candidate is written to the source and the lifecycle transitions.
6. **Watermark** — the last processed trace is recorded, so a re-run over unchanged traces
   publishes nothing.

### Gate order (cheap first)

A candidate must clear every gate before it may be published, run cheap-first so a broken or
unsafe candidate never spends eval budget:

1. **D5 validation** — structural validation of the candidate (free, deterministic).
2. **Security scan** — the static scanner; a critical finding blocks (free, deterministic).
3. **Re-eval** — the candidate is deployed and run against the eval tasks (costs model calls).
4. **Baseline comparison** — the pass-rate must be *strictly* greater than the pack's baseline.

A rejection names the stage and records the reasons (and, when reached, the score and scan).

### Lifecycle

The sidecar's state moves as the spec improves and regresses (POC 18, carried into the
product):

```
incubating --first proven improvement--> active --regression--> degraded --sustained regression--> retired
                                              ^                                |
                                              +--- new gated candidate published +
```

- `incubating → active` on a first proven improvement (a gated candidate is published).
- `active → degraded` when a live re-eval scores below the baseline.
- `degraded → retired` when the next re-eval fails again.
- `degraded → active` when a new gated candidate is published (recovery).

The state is advisory — the runtime enforces trust tiers, not lifecycle state.

### Approval semantics

The default is **hold**: a gated candidate is queued in the `ApprovalQueue`, not published.
Approving a candidate publishes it and transitions the lifecycle; rejecting discards it. An
`autoPublish` mode publishes gated candidates immediately, leaving the queue empty.

### v1 limitations

- **In-memory queue** — the `ApprovalQueue` and the per-spec watermarks live in memory; a
  restart loses pending candidates and reprocesses the window from the start.
- **Advisory lifecycle** — the sidecar state is recorded but not enforced; only the runtime's
  trust tiers are binding.
- **No per-spec error isolation** — a failure while refining, gating, or publishing one spec
  aborts the pass; that spec's watermark is not advanced, so it is retried on the next run, but
  the specs later in the pass wait for the next interval.

## Frontend

The SPA lives in `frontend/` (Vue 3 + Quasar + Vite + TypeScript) and keeps the POC 22
architecture: pure logic (the `traces/model.ts` and `improve/model.ts` modules, the
`useSourceSpec` composable) separated from the views, a single `fetch` site in
`frontend/src/api/client.ts`, and a TypeScript type contract (`frontend/src/api/types.ts`)
mirroring the REST DTOs.

```
cd prod/embabel-smith-studio/frontend
npm install
npm run typecheck   # vue-tsc --noEmit
npm test            # vitest
npm run build       # emit into src/main/resources/static
```
