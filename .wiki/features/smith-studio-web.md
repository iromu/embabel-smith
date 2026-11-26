---
title: "The smith studio web product: REST surface and UI"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/RunController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/ImproveController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopService.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/loop/LoopScheduler.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-studio/frontend/src/api/client.ts"
  - "prod/embabel-smith-studio/frontend/src/panes/AutoImprovePane.vue"
  - "prod/embabel-smith-studio/frontend/src/panes/StoragePane.vue"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StorageSettingsController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/BuildController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StatusController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/SpaWebConfig.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/RetrieveController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/RemoteController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/dto/RetrieveResponse.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/SpecScorer.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/LexicalScorer.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/SpecRanker.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/RetrievalCuration.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/SpecEntries.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/retrieval/SpecEntry.java"
  - "prod/embabel-smith-studio/frontend/src/panes/ScalePane.vue"
  - "prod/embabel-smith-studio/frontend/src/router.ts"
  - "prod/embabel-smith-studio/frontend/vite.config.ts"
  - "prod/embabel-smith-studio/pom.xml"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/NoA2aEndpointTest.java"
  - "prod/embabel-smith-studio/src/test/java/com/embabel/smith/studio/A2aEndpointWhenEnabledTest.java"
  - "prod/embabel-smith-studio/README.md"
updated_at: "2026-11-26"
---

# The smith studio web product

Change 06 turns the studio's self-improvement loop ([[smith-studio]]) into a web app: a Spring Boot
application that hosts the production cores — run a goal ([[smith-runtime]]), build a pack
([[smith-builder]]), and operate the loop — behind a JSON REST surface, with a Vue 3 + Quasar front
end. It is the production counterpart of POC 22's unified builder UI, re-pointed at the production
cores instead of POC launchers. The module also declares `embabel-smith-a2a-starter`, because this is
the repo's long-lived servlet process and therefore the process where the A2A surface gets switched on
([[a2a-endpoint]]): with no flag set the starter contributes no controller and no mapping here, and
what keeps the surface shut is `smith.a2a.enabled` plus the starter's exclusion of Embabel's own A2A
auto-configuration — not the classpath.

## Thin controllers, cores do the work

Each REST controller is a thin `@RestController` that delegates to a production core
(`AgentSpecRunService`, `BuilderService`, `ImprovementLoop`). Model-backed collaborators are injected
as narrow functional seams (`GenerateFn`, `SimulateFn`, …) so the controllers are offline-testable
without a live model. Beyond the run / build / sources / packs / traces / candidates / loop endpoints,
the authoring surface added `/api/discover`, `/api/generate`, `/api/simulate`, `/api/extract`,
`/api/compose`, `/api/store/*`, `/api/goals` and `/api/status` — see
[[studio-process-workspace]] and [[studio-compose-editor]].

`StudioConfiguration` wires the `BuilderService` with the agents-pack generator
(`SingleShotAgentsPackGenerator`) too, so a studio build
runs the full [[two-candidate-build]]. `BuildController` is the one place that still pins the target:
it constructs `BuildOptions` with `BuildTarget.AGENT_SPEC` and `DEFAULT_MIN_LIFT`, so the UI has no
skill-target switch yet ([[skill-output-target]]).

The module owns **no model caller of its own**. Beans built from the builder's seams do all the model
work: `specPromptCaller(Ai)` for the workspace's Extract step (12 000-token budget, temperature 0.2 —
the budget the studio's own caller used to carry), `promptRewriter(Ai)` for the Eval pane's
refinement and the self-improvement loop, and the builder's generators — which call the same `Ai` —
for Generate ([[studio-process-workspace]]). The studio's raw chat-completions caller is deleted, and
a source-level test fails the build if anything under `src/main/java` reintroduces a direct endpoint
call ([[model-calls-go-through-ai]]).

## Retrieve and Remote: two panes whose logic lives elsewhere

Both were POC-only capabilities that are now wired, and neither pane holds any of its own logic —
which is the point of listing them next to the cores.

**Retrieve** (`/retrieve`, `POST /api/retrieve`). `rest/RetrieveController` lists a source's packs,
reads each into a lightweight index entry (`retrieval/SpecEntries.of`), ranks them against the task
query, and only then returns the top-k. Before that it curates the ranking with each pack's recorded
lifecycle state, applying the two rules POC 18 measured (`builder/retrieval/RetrievalCuration.java`):
a `retired` pack is **dropped** (only retirement removes a pack from the library), and a `degraded`
pack's score is **halved** — after which the list is re-ordered, because a halved score can sink below
a pack that had ranked under it. A pack with no `lifecycle.yml` sidecar reports `UNKNOWN` and is
surfaced with its score unchanged; a pack that cannot be read contributes no entry rather than failing
the query, so an empty source is an empty result and not a fault. See [[spec-lifecycle]] for where
that state comes from and [[retrieval-shadowing]] for the POC this was copied from.

Scoring sits behind a one-method seam — `builder/retrieval/SpecScorer` is a `@FunctionalInterface`
with a single `score(query, entry)` — and `LexicalScorer` (cosine over term-frequency vectors of
`name + description`) is the one implementation that ships. The controller names that scorer as
`"lexical"` on every successful response, so swapping in a denser scorer is a wiring change rather
than a contract change; the structured-error response carries no scorer value at all
(`dto/RetrieveResponse.java` — the field is documented as null on failure). Ranking (`SpecRanker`),
entry-shaping and the curation arithmetic all live in the **builder's** `retrieval/` package; the
controller contributes the source read and the DTO shaping and none of the arithmetic.

**Remote** (`/remote`, `GET /api/remote/bridges` + `POST /api/remote/execute`). The controller reads
the `smith.studio.remote-bridges` configuration, probes each configured sidecar, and runs one action a
request names — every line of the wire protocol is the runtime's `remote/` package
([[smith-runtime]]). Three studio-side rules are load-bearing:

- **A request names a bridge; it never supplies an address.** The controller matches on the
  *configured* name only, and a name configuration does not define is refused before anything is
  contacted — `RemoteControllerTest` proves that by asserting the stub sidecar's request count is
  zero on the refusal path. Without the rule the pane is an open proxy: anyone who can reach it could
  point this host at any URL.
- **An unreachable sidecar is a state inside a `2xx`**, never a failed request — one sidecar being
  down is not a fault in this application.
- **Nothing gets started, nothing else gets invoked.** No sidecar process is ever spawned (a
  browser-reachable tool that launches processes is a remote-code-execution surface, and spawning
  would make Docker a product dependency), and the configured MCP endpoint is displayed as configured
  but never probed — this product is not an MCP client, so a reachable badge on it would lie. The
  product also does not *plan* over a remote action; the three reasons are on [[smith-runtime]].

That last split is enforced, not stylistic: the module-wide `NoDirectModelRequestTest` fails the build
if any studio production source imports `java.net.http.`, which is why neither controller talks to
anything itself.

## The front end

Evolved from POC 22's SPA (Vue 3.5 + Quasar 2.17 + Vite + TypeScript under
`prod/embabel-smith-studio/frontend/`; `npm run build` runs `vue-tsc --noEmit` then emits into
`src/main/resources/static`, which is gitignored and served from the classpath — Maven drives the
build via `frontend-maven-plugin`, pinning node in `frontend/.nvmrc`). The app is **dark by
construction**: `main.ts:25-26` removes `body--light` and adds `body--dark`, with the palette in
`quasar-variables.sass` injected ahead of Quasar's own. Shared chrome lives in
`components/PageHeader.vue`, `SectionCard.vue`, and `EmptyState.vue`.

Fifteen routes over fourteen panes (`frontend/src/router.ts` — `/` and `/process` are the same
pane), of which **thirteen are backed** and one is a stub:

- **Process** (`/`, aliased `/process`) — the home wizard: discover, generate, simulate, publish.
  This is the landing page, not a dashboard. See [[studio-process-workspace]].
- **Compose** (`/compose`) — a Vue Flow GOAP editor that exports an assembled flow. See
  [[studio-compose-editor]].
- **Six panes re-pointed at the studio REST:** Run, Author, Eval, Lifecycle, Security, Trust.
- **Traces** — per-spec trace history (most-recent-first), step drill-down, failure reason.
- **Auto-improve** — loop status, pending candidates with their evidence, approve/reject,
  auto-publish toggle, lifecycle badges.
- **Storage** — point the studio at its Postgres database (endpoint/user/password) and pick the
  trace backend; save validates the connection and rebinds live, keeping the old binding on failure
  ([[smith-studio-storage]]).
- **Retrieve** — ranks a source's stored packs against a task by lexical score with lifecycle
  curation, offline, through `POST /api/retrieve` (`rest/RetrieveController.java`).
- **Remote** — lists the sidecars this installation configured, whether each answers, and the output
  of running one of its actions, over `GET /api/remote/bridges` and `POST /api/remote/execute`
  (`rest/RemoteController.java`).
- **Scale** — the one POC-only pane: a wrapper around `PocOnlyPane.vue` (POC 12), carried as a
  disabled reference stub because the production core has no such feature.

Two small endpoints feed the chrome rather than a pane: `GET /api/status`
(`rest/StatusController.java:31`) reports model-endpoint readiness as a header chip and drawer badge
(`App.vue:75-80,97-106,190`), and `GET /api/goals?source=` (`rest/SourceController.java:57`) lists
the exported goals so the Run pane can pick one and auto-select it when exactly one exists
(`panes/RunPane.vue:115-133`).

Pure logic (trace-table filtering, candidate evidence formatting, loop state, the compose graph
model) lives in non-`.vue` modules (`traces/model.ts`, `improve/model.ts`, `compose/model.ts`); there
is one fetch site (`api/client.ts`) and a TypeScript contract (`types.ts`) mirroring the DTOs,
checked by `vue-tsc --noEmit` + Vitest.

## Serving an SPA from Spring

The router uses HTML5 history mode, so a deep link like `/improve` arrives at Spring with no
controller behind it. `config/SpaWebConfig.java:29-46` forwards unknown paths to the SPA shell — with
`api/` deliberately **excluded**, so a mistyped endpoint still returns a real 404 instead of the
index page. The other half of that rule matters to a guard rather than to a user: every unmapped path
outside `api/` answers **200** with the shell (405 to a `POST`), so a status code cannot prove an
endpoint is absent in this application — which is why `NoA2aEndpointTest` asks whether any request
mapping is registered under `/a2a` rather than asking for a 404 ([[a2a-endpoint]]). Separately,
`SmithStudioApplication.main` forces `spring.main.web-application-type=SERVLET`
as a *system property*; the reason is non-obvious enough that it is documented on the storage page
([[smith-studio-storage]]).

Dev runs use Vite on 5173 proxying `/api` to `http://localhost:8080` (`frontend/vite.config.ts:31`) —
which is the app's own default (`src/main/resources/application.yml:2`, `${SERVER_PORT:8080}`), so the
pair works with nothing set in the environment. That target is hardcoded, though: move the app to any
other port and the dev server proxies into nothing until that line moves with it. The POC run book's
`SERVER_PORT=18080` habit is exactly that case, 8080 being commonly taken on developer machines.

## Loop scheduling

The interval scheduler is a `@Scheduled` tick gated by `LoopSchedulerState`, **off by default**. It
calls `ImprovementLoop.runOnce()` every `smith.studio.loop.interval` (default 60s) only while the
schedule is running. The UI starts/stops the schedule and can trigger a single on-demand iteration.
`LoopService` also implements auto-publish: when the toggle is on, each iteration immediately
approves the candidates it produced. The `ApprovalQueue` is thread-safe (the scheduler tick and the
REST threads can touch it at once), and `approve()` publishes before removing so a failed publish
leaves the candidate pending rather than silently losing it.

## Model-not-configured degradation

If `OPENAI_CUSTOM_*` is absent the app still boots and the offline panes (sources, traces,
lifecycle, loop status, candidate review) work. Model-backed actions (run, build, loop iterations
that re-evaluate) return a structured `MODEL_NOT_CONFIGURED` error the UI renders as a banner
([[model-endpoint]]).

**Tests:** controller tests
(`Run/Build/Source/Trace/Improve/Eval/Security/Trust/Retrieve/Remote ControllerTest`)
with faked cores, `LoopSchedulerTest` (fresh-start quiet / start / stop), `ModelNotConfiguredTest`
(full app boots without an endpoint, and the model-backed run, build and extract endpoints each
return the structured error — `ExtractController` was the last one without such a test),
`NoDirectModelRequestTest` (no production source calls the endpoint directly),
`ApprovalQueueConcurrencyTest` + `ApprovalQueueTest`
(publish-failure recovery), the endpoint-gated `RestIntegrationTest`, and the front-end Vitest
suite.
