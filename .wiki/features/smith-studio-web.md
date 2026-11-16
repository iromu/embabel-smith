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
  - "prod/embabel-smith-studio/frontend/src/router.ts"
  - "prod/embabel-smith-studio/README.md"
updated_at: "2026-11-15"
---

# The smith studio web product

Change 06 turns the studio's self-improvement loop ([[smith-studio]]) into a web app: a Spring Boot
application that hosts the production cores — run a goal ([[smith-runtime]]), build a pack
([[smith-builder]]), and operate the loop — behind a JSON REST surface, with a Vue 3 + Quasar front
end. It is the production counterpart of POC 22's unified builder UI, re-pointed at the production
cores instead of POC launchers.

## Thin controllers, cores do the work

Each REST controller is a thin `@RestController` that delegates to a production core
(`AgentSpecRunService`, `BuilderService`, `ImprovementLoop`). Model-backed collaborators are injected
as narrow functional seams (`GenerateFn`, `SimulateFn`, …) so the controllers are offline-testable
without a live model. Beyond the run / build / sources / packs / traces / candidates / loop endpoints,
the authoring surface added `/api/discover`, `/api/generate`, `/api/simulate`, `/api/extract`,
`/api/compose`, `/api/store/*`, `/api/goals` and `/api/status` — see
[[studio-process-workspace]] and [[studio-compose-editor]].

`StudioConfiguration` wires the `BuilderService` with the process generator too, so a studio build
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

## The front end

Evolved from POC 22's SPA (Vue 3.5 + Quasar 2.17 + Vite + TypeScript under
`prod/embabel-smith-studio/frontend/`; `npm run build` runs `vue-tsc --noEmit` then emits into
`src/main/resources/static`, which is gitignored and served from the classpath — Maven drives the
build via `frontend-maven-plugin`, pinning node in `frontend/.nvmrc`). The app is **dark by
construction**: `main.ts:17-21` removes `body--light` and adds `body--dark`, with the palette in
`quasar-variables.sass` injected ahead of Quasar's own. Shared chrome lives in
`components/PageHeader.vue`, `SectionCard.vue`, and `EmptyState.vue`.

Fifteen routes over fourteen panes (`frontend/src/router.ts` — `/` and `/process` are the same
pane), of which **eleven are backed** and three are stubs:

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
- **Three POC-only panes** (Retrieve, Scale, Remote) — each an 11-line wrapper around
  `PocOnlyPane.vue`, carried as disabled reference stubs because the production cores have no such
  feature.

Two small endpoints feed the chrome rather than a pane: `GET /api/status`
(`rest/StatusController.java:42`) reports model-endpoint readiness as a header chip and drawer badge
(`App.vue:94-101,186`), and `GET /api/goals?source=` (`rest/SourceController.java:68`) lists exported
goals so the Run pane can pick one and auto-select it when exactly one exists
(`panes/RunPane.vue:102-118`).

Pure logic (trace-table filtering, candidate evidence formatting, loop state, the compose graph
model) lives in non-`.vue` modules (`traces/model.ts`, `improve/model.ts`, `compose/model.ts`); there
is one fetch site (`api/client.ts`) and a TypeScript contract (`types.ts`) mirroring the DTOs,
checked by `vue-tsc --noEmit` + Vitest.

## Serving an SPA from Spring

The router uses HTML5 history mode, so a deep link like `/improve` arrives at Spring with no
controller behind it. `config/SpaWebConfig.java:44-58` forwards unknown paths to the SPA shell — with
`api/` deliberately **excluded**, so a mistyped endpoint still returns a real 404 instead of the
index page. Separately, `SmithStudioApplication.main` forces `spring.main.web-application-type=SERVLET`
as a *system property*; the reason is non-obvious enough that it is documented on the storage page
([[smith-studio-storage]]).

Dev runs use Vite on 5173 proxying `/api` to `http://localhost:18080` (`frontend/vite.config.ts:29-33`),
while the app's own port defaults to **8080** — so the README's `SERVER_PORT=18080` is not optional
advice, and the proxy target is hardcoded. Note 8080 is commonly taken on developer machines.

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

**Tests:** controller tests (`Run/Build/Source/Trace/Improve/Eval/Security/Trust ControllerTest`)
with faked cores, `LoopSchedulerTest` (fresh-start quiet / start / stop), `ModelNotConfiguredTest`
(full app boots without an endpoint, and the model-backed run, build and extract endpoints each
return the structured error — `ExtractController` was the last one without such a test),
`NoDirectModelRequestTest` (no production source calls the endpoint directly),
`ApprovalQueueConcurrencyTest` + `ApprovalQueueTest`
(publish-failure recovery), the endpoint-gated `RestIntegrationTest`, and the front-end Vitest
suite.
