# Tasks

## 0. Prerequisite

- [x] 0.1 Confirm POC 22's tail tasks (7.3 heavy-scorer opt-in, 7.4 remote gating) are verified and `poc-22-unified-ui` is complete — the studio frontend starts from that finished SPA

## 1. Application skeleton

- [x] 1.1 Create the Spring Boot application in `prod/embabel-smith-studio` (boot class, `application.yml`: port, `smith.studio.loop.interval`, trace-dir, source-cache-dir properties)
- [x] 1.2 Wire the production cores as beans: `SkillRunService`, `BuilderService`, `ImprovementLoop` + `ApprovalQueue`, `LoopSchedulerState`

## 2. REST surface

- [x] 2.1 `RunController` (source, goal, input, planner, trust tier) + DTOs + offline tests
- [x] 2.2 `BuildController` (description, output source) + build-report DTO + offline tests
- [x] 2.3 `SourceController` (list sources, list packs with lifecycle state + drift status) + offline tests
- [x] 2.4 `TraceController` (per-skill trace window, run detail) + offline tests
- [x] 2.5 `ImproveController` (candidates list, approve, reject, loop trigger/start/stop, auto-publish get/set) + offline tests
- [x] 2.6 Model-not-configured path: app boots without `OPENAI_CUSTOM_*`; model-backed endpoints return the structured `MODEL_NOT_CONFIGURED` error + test

## 3. Frontend (evolved from POC 22)

- [x] 3.1 Copy POC 22's `frontend/` into `prod/embabel-smith-studio/frontend/`; build to `static/`; `vue-tsc --noEmit` + `vitest` green as the baseline
- [x] 3.2 Re-point `api/client.ts` + `types.ts` at the studio REST endpoints; re-point the six backed panes (Run, Author, Eval, Lifecycle, Security, Trust) and carry the four POC-only panes (Compose, Retrieve, Scale, Remote) as disabled reference stubs
- [x] 3.3 Add the **Traces** pane (per-skill trace table, most-recent-first, step drill-down dialog, failure reason) + pure model module + tests
- [x] 3.4 Add the **Auto-improve** pane (loop status, pending candidates with evidence, approve/reject, auto-publish toggle, lifecycle badges) + pure model module + tests
- [x] 3.5 Model-not-configured banner on model-backed panes + test

## 4. Loop wiring

- [x] 4.1 Implement the interval scheduler (`@Scheduled`, gated by `LoopSchedulerState`, off by default) calling `ImprovementLoop.runOnce()`
- [x] 4.2 On-demand trigger + start/stop + auto-publish endpoints wired to the loop (covered by 2.5; verify end-to-end with a faked loop)
- [x] 4.3 Test: fresh start runs no iterations; start → iterations at interval; stop halts them

## 5. Verify + docs

- [x] 5.1 `mvn -pl prod/embabel-smith-studio test` green offline; frontend `vitest` + `vue-tsc --noEmit` green
- [x] 5.2 Endpoint-gated integration test (skipped without `OPENAI_CUSTOM_*`): run a goal via REST, build a pack via REST, one loop iteration yields a pending candidate via the REST trigger
- [x] 5.3 Write `prod/embabel-smith-studio/README.md` (boot, panes, loop operation, local-port note, model-not-configured behavior)
- [x] 5.4 Full reactor `mvn -q verify` green from the root
