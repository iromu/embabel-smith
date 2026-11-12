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
  - "prod/embabel-smith-studio/frontend/src/api/client.ts"
  - "prod/embabel-smith-studio/frontend/src/panes/AutoImprovePane.vue"
  - "prod/embabel-smith-studio/README.md"
updated_at: "2026-11-08"
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
as narrow functional seams so the controllers are offline-testable without a live model. The
endpoints: run (source, goal, input, planner, trust tier), build (description, output source), list
sources and packs (with lifecycle state + drift), list per-spec traces, list/approve/reject pending
candidates, and loop start/stop/trigger plus auto-publish get/set.

## The front end

Evolved from POC 22's SPA (Vue 3 + Quasar + Vite + TypeScript; `npm run build` emits into
`src/main/resources/static`, which is gitignored). The panes:

- **Six backed panes** re-pointed at the studio REST: Run, Author, Eval, Lifecycle, Security, Trust.
- **Traces** — per-spec trace history (most-recent-first), step drill-down, failure reason.
- **Auto-improve** — loop status, pending candidates with their evidence, approve/reject,
  auto-publish toggle, lifecycle badges.
- **Four POC-only panes** (Compose, Retrieve, Scale, Remote) carried as disabled reference stubs —
  the production cores have no such features.

Pure logic (trace-table filtering, candidate evidence formatting, loop state) lives in non-`.vue`
modules (`traces/model.ts`, `improve/model.ts`); there is one fetch site (`api/client.ts`) and a
TypeScript contract (`types.ts`) mirroring the DTOs, checked by `vue-tsc --noEmit` + Vitest.

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
(full app boots without an endpoint), `ApprovalQueueConcurrencyTest` + `ApprovalQueueTest`
(publish-failure recovery), the endpoint-gated `RestIntegrationTest`, and the front-end Vitest
suite.
