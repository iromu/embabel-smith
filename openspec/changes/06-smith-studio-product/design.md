# Design

## Shape

```
+---------------------------------------------------------------------+
| prod/embabel-smith-studio (Spring Boot)                                     |
|                                                                     |
|  frontend/ (Vue 3 + Quasar + Vite + TS — evolved from POC 22)       |
|   panes: Compose Retrieve Security Trust Scale Run Author           |
|           Eval Lifecycle Remote   (10 re-pointed)                   |
|           + Traces + Auto-improve                                   |
|   api/client.ts  types.ts  pure model modules (vitest)              |
|        |  fetch                                                 ^   |
|        v  JSON                                              static/ |
|  @RestController (thin — POC 22 pattern: delegate, no logic)        |
|   RunController        --> SkillRunService     (change 03)          |
|   BuildController      --> BuilderService      (change 04)         |
|   SourceController     --> SkillSource         (change 02)         |
|   TraceController      --> TraceReader         (change 03 store)   |
|   ImproveController    --> ImprovementLoop + ApprovalQueue (05)    |
|   LoopScheduler (@Scheduled, off by default) --> ImprovementLoop   |
+---------------------------------------------------------------------+
```

## Decisions

### Thin controllers, cores do the work
POC 22's proven pattern: each pane's controller is a thin `@RestController` that delegates to the proven core classes, with model-backed collaborators injected as narrow functional interfaces so controllers are offline-testable without a live model. Studio keeps exactly that pattern — the only difference is the delegates are the production cores (`SkillRunService`, `BuilderService`, `ImprovementLoop`) instead of POC classes. No POC module is on the classpath (change 01's enforcer forbids it).

### Frontend: evolve POC 22's SPA, don't restart
Copy POC 22's finished `frontend/` as the starting point, then:
- Re-point `api/client.ts` at the studio REST endpoints (same shape where the panes map 1:1 — run, build/author, eval, lifecycle, security, trust, compose, retrieve, scale, remote).
- Add two panes: **Traces** (per-skill trace table, step drill-down dialog — Quasar `q-table` + `q-dialog`) and **Auto-improve** (loop status card, pending-candidate table with evidence expansion, approve/reject buttons, auto-publish toggle, lifecycle badges).
- Keep the architecture contract: pure logic in non-`.vue` modules, one fetch site, `types.ts` mirroring the DTOs, `vue-tsc --noEmit` + Vitest in the build.

Sequencing: POC 22 has two verification tasks left (7.3 scorer opt-in, 7.4 remote gating). Land those first so the SPA being copied is fully verified; studio's frontend work then starts from green.

### Loop control surface
`ImproveController`: `GET /api/improve/candidates`, `POST /api/improve/candidates/{id}/approve|reject`, `POST /api/loop/trigger`, `POST /api/loop/start|stop`, `GET+PUT /api/loop/auto-publish`. The scheduler is a `@Scheduled(fixedDelayString)` bean gated by a `LoopSchedulerState` (enabled flag, default false) — start/stop flips the flag; trigger calls `runOnce()` directly. Interval is a property (`smith.studio.loop.interval`, default 60s).

### Model-not-configured degradation
If `OPENAI_CUSTOM_*` is absent, the app boots and the UI works for everything offline (sources, traces, lifecycle, loop status, candidate review — all read from disk). Model-backed actions return a structured `MODEL_NOT_CONFIGURED` error the UI renders as a banner. This matches the POC suite's endpoint-gating discipline and makes the product usable on a machine without an endpoint.

### Port
`server.port` follows the standard Spring convention (default 8080, `SERVER_PORT` override). Local-run docs note picking a free high port when 8080 is occupied.

## Testability

- Backend offline: controller tests with faked service collaborators (POC 22 pattern), DTO shape tests, scheduler state transitions, model-not-configured error path.
- Frontend: Vitest on the pure modules (pane state models, candidate evidence formatting, trace table filtering); `vue-tsc --noEmit` in the build.
- Endpoint-gated (IT, skipped without env): run a goal through the REST API, build a pack through the REST API, one loop iteration producing a pending candidate via the REST trigger.
