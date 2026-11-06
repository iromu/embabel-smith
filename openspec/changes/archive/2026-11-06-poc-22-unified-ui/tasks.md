# Tasks

## 1. Scaffold the module

- [x] 1.1 Create `poc/poc-22-unified-ui/` (pom.xml, `src/main/java`, `src/main/resources`, `frontend/`) and register it in `poc/pom.xml`; verify `mvn -pl poc/poc-22-unified-ui -q validate` passes and the module appears in the reactor
- [x] 1.2 Add dependencies to the pom — the light POCs (poc-1, poc-2, poc-9, poc-10, poc-12, poc-14, poc-18, poc-21), the remote POCs (poc-13, poc-16), `smith-common`, and `spring-boot-starter-web` / `-test`; verify `mvn -pl poc/poc-22-unified-ui -q dependency:resolve` succeeds (the heavy scorers poc-17/19/20 are NOT added by default — they are opt-in, see 7.3)
- [x] 1.3 Scaffold the frontend (Vite + Vue 3 + Quasar + TS + Vitest) under `frontend/` with `vite.config.ts` (build outDir → `../src/main/resources/static`; dev proxy `/api` → `:18080`), `tsconfig.json`, `index.html`, `src/main.ts`, `src/App.vue` (Quasar layout + nav), `src/router.ts` (10 routes); verify `npm run build` emits `src/main/resources/static/` and `npx vue-tsc --noEmit` passes
- [x] 1.4 Add `poc/poc-22-unified-ui/README.md` with a mermaid diagram of the architecture (SPA → thin REST → proven POC modules); verify the mermaid block parses (the repo's headless-mermaid check)

## 2. Canvas model (pure, unit-tested)

- [x] 2.1 Implement `frontend/src/canvas/model.ts` — the type-checked snap rule + chain validation as pure functions (`{nodes, edges, skills} → {valid, issues, chain}`), mirroring POC 14's edge type-check; verify with unit tests that a type-compatible edge is valid and a type-incompatible edge is flagged with the reason
- [x] 2.2 Add unit tests for chain composition (a valid single chain, disconnected nodes, a cycle) in `frontend/src/__tests__/canvas.model.test.ts`; verify `npx vitest run canvas.model` passes

## 3. Backend thin controllers + DTOs

- [x] 3.1 Implement the offline panes' controllers + DTOs — Compose (poc-14), Retrieve (poc-12 lexical + poc-18 curation), Security (poc-21), Trust (poc-10), Scale (P@1 table); verify each with a MockMvc test asserting the response shape
- [x] 3.2 Implement the endpoint-gated panes' controllers + DTOs — Run (poc-1 GOAP), Author (poc-2), Eval (poc-9), Lifecycle (poc-18 re-eval) — returning a clear "endpoint not configured" body when the model env vars are absent; verify each with a MockMvc test covering the gated path and a (mocked) endpoint-present path
- [x] 3.3 Implement the Remote pane's controller (poc-16 RestAction / poc-13 MCP), gated on sidecar/MCP reachability, returning a "backing process not running" body when down; verify with a MockMvc test covering the down path
- [x] 3.4 Implement the scorer-swap wiring so a denser scorer (poc-17/19/20) can replace the lexical default via the `SkillScorer` seam, with the Retrieve response carrying the active scorer name; verify with a test that swapping the scorer changes the reported name and the ranking

## 4. Frontend API layer (typed, unit-tested)

- [x] 4.1 Implement `frontend/src/api/types.ts` (mirroring the backend DTOs) and `frontend/src/api/client.ts` (the single `fetch` site, one typed method per pane); verify `npx vue-tsc --noEmit` passes
- [x] 4.2 Add unit tests in `frontend/src/__tests__/client.test.ts` mocking `fetch` to assert request shaping and response parsing for a representative set of panes; verify `npx vitest run client` passes

## 5. Quasar panes + component tests

- [x] 5.1 Implement the offline panes (Compose, Retrieve, Security, Trust, Scale) as thin `.vue` SFCs over `canvas/model.ts` + `api/client.ts`; verify `npx vue-tsc --noEmit` passes
- [x] 5.2 Implement the endpoint-gated panes (Run, Author, Eval, Lifecycle) with the endpoint-not-configured state; verify `npx vue-tsc --noEmit` passes
- [x] 5.3 Implement the Remote pane with the backing-process-not-running state; verify `npx vue-tsc --noEmit` passes
- [x] 5.4 Add component tests (Vitest + `@vue/test-utils`) for 2-3 representative panes (Compose, Retrieve, one endpoint-gated pane) that mount the SFC, fill inputs, and assert the API is called + the result rendered; verify `npx vitest run` (component suite) passes

## 6. Build + serving wiring

- [x] 6.1 Add `src/main/resources/static/` to `.gitignore` and wire `npm run build` into the module build (frontend-maven-plugin or a documented pre-step); verify a clean `git status` shows no committed `static/` and the running app serves the built SPA
- [x] 6.2 Verify the dev flow — `vite` dev server on `:5173` proxies `/api` to the Spring app on `:18080`

## 7. Integration verification

- [x] 7.1 Boot the app (`SERVER_PORT=18080`) and verify all offline panes work end-to-end with no model endpoint (compose a chain, retrieve, scan a pack, trust-tier check, scale table)
- [x] 7.2 With the model endpoint configured (the LiteLLM bridge env vars), verify the endpoint-gated panes (Run, Author, Eval, Lifecycle) drive their POCs and render results
- [x] 7.3 Verify the heavy-scorer opt-in — add one denser scorer dep (e.g., poc-20 embedding), wire it via the seam, and confirm the Retrieve pane reports it as active and ranks by it
- [x] 7.4 Verify the remote gating — with the sidecar down the Remote pane shows "not running"; with it up, invoking the remote action returns its result
