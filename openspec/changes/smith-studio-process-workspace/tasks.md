# Tasks

## 1. Generation core

- [x] 1.1 `FlowPromptCaller` seam + `LlmFlowPromptCaller` (raw chat-completions, bounded tokens/timeout, temperature)
- [x] 1.2 `BlueprintLoader` — resolve a blueprint from text, a docs path (recursive .md/.txt, capped), or a repo URL
- [x] 1.3 `process_to_flow.jinja` prompt — one `SkillPack` JSON (manifest/goal/steps/types), GOAP wiring rules, decapitalized prompt-var convention
- [x] 1.4 `ProcessFlowGenerator` — call the model, extract + parse the JSON, normalise (manifest name, exported goal) and **canonicalise prompt vars**
- [x] 1.5 `FlowValidator` — goal exported, ≥1 step, goal output produced, every step input produced or `UserInput`
- [x] 1.6 Offline test: the real model answer canonicalises to a pack that passes `SkillPackValidator` and is discoverable by `FolderSkillSource`

## 2. Backend REST

- [x] 2.1 `GenerateController` (`POST /api/generate`) + `GenerateRequest`/`GenerateResponse` (pack, issues, error, errorCode) + endpoint-gating
- [x] 2.2 `SimulateController` (`POST /api/simulate`) + `SimulateRequest`/`SimulateResponse` (per-step trace, goal output) + endpoint-gating
- [x] 2.3 `DiscoverController` (`POST /api/discover`) + `DiscoverRequest`/`DiscoverResponse` (docs map)
- [x] 2.4 `BusinessProcessDiscovery` — load a local docs dir or git-clone a repo URL (depth 1) to a local copy
- [x] 2.5 `StudioConfiguration` beans: `processFlowGenerator`, `generateFn`, `simulateFn` (throwaway folder source via the runtime core), `businessProcessDiscovery`, `loadPrompt`

## 3. Frontend workspace

- [x] 3.1 `compose/model.ts` — `packToNodes` (topological layered layout: column from producers, vertical fan-out, goal rightmost; edges from type wiring)
- [x] 3.2 `ProcessWorkspace.vue` — blueprint input (text / docs path / repo URL), auto/manual toggle, Load docs + Generate, progress, Vue Flow graph, step/goal side editor, simulate panel, publish
- [x] 3.3 `api/types.ts` + `api/client.ts` — Generate/Simulate/Discover request + response types and calls
- [x] 3.4 `router.ts` — `/` (and `/process`) → ProcessWorkspace; existing panes retained
- [x] 3.5 `App.vue` — "Studio" nav group with "Author a process" first; POC panes demoted to secondary groups

## 4. Verification

- [x] 4.1 `vue-tsc --noEmit` clean; SPA builds
- [x] 4.2 Backend + frontend build (`mvn` + `npm run build`)
- [x] 4.3 Live E2E (Playwright) on the TradingAgents docs: home → load docs → generate (layered flow) → simulate (per-step trace)
- [x] 4.4 Fix the simulate "contains no skill-packs" failure (root cause: snake_case prompt vars failed validation → pack excluded); verified end-to-end

## 5. Deferred (out of scope for this pass)

- [ ] 5.1 Model-based process *extraction* via `business_process_discovery.jinja` (discover loads docs; extraction is a follow-up)
- [ ] 5.2 Durable store for generated packs / discovery history (per-run and ephemeral for now)
