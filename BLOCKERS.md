# Blockers & Work Log — Studio "Author a Process" UI

Autonomous build (2026-11-11). Goal: a working home-screen workspace in
`prod/embabel-smith-studio` that generates an agentic (GOAP) process from business
documents, renders it as an editable flow, can simulate a run, and publishes a pack.
Test input: `/home/wantez/source/github/TauricResearch/TradingAgents/docs/business`.

## Status: WORKING — generate + simulate verified end-to-end (2026-11-11)

The home-screen workspace generates a GOAP process from the TradingAgents docs,
renders it as a clean editable flow, simulates a run (full per-step trace), and can
publish a pack. Verified live on port 18099 with a Playwright E2E and direct curl.
Remaining: OpenSpec "capture" of the design, and optional model-based discovery
(extraction via `business_process_discovery.jinja`) — see Plan items 9–10.

## Design (decided with the user)
- **One pack** — the whole process is a single skill-pack (goal + steps + types).
  "Skills" = the steps; "tools" deferred (no-code format has no LLM-tool concept;
  "tool" already means trust-tier group).
- **Home screen** — the author-a-process workspace replaces the 13-pane POC sidebar
  as the landing page; the old panes are demoted to a secondary "Operations" nav.
- **Model proposes the split** — the model decides the step decomposition.
- **Discovery ingestion** — a second blueprint source: point at a repo URL / zip /
  local docs dir; `business_process_discovery.jinja` extracts the business process;
  an auto/manual toggle controls whether generation runs straight after.

## Plan
1. [x] Backend: `/api/generate` (blueprint/path -> SkillPack JSON), generation prompt
2. [x] Spike generation against the TradingAgents docs; iterate on the prompt for a nice flow
3. [x] Backend: `/api/simulate` (pack + input -> per-step trace) via SkillRunService
4. [x] Backend: `/api/discover` (path/url -> docs + process inventory)
5. [x] Frontend: ProcessWorkspace home page (blueprint input, progress, flow graph, skills, simulate, publish)
6. [x] Frontend: types.ts + client.ts for the new endpoints; router + App nav
7. [x] Build SPA, run app, Playwright the pipeline, verify the flow (programmatically — see Blockers), iterate
8. [x] DB (docker) — NOT needed for v1; packs/docs are ephemeral per run (see note below)
9. [x] OpenSpec: capture the design — change `07-smith-studio-process-workspace`
10. [ ] Optional: model-based discovery — run `business_process_discovery.jinja` against a raw
    codebase/docs dir to extract the process (currently `/api/discover` only loads existing docs)

## Blockers (things I could not resolve on my own)
- **DB not needed for v1** — generated packs and loaded docs are per-run and ephemeral
  (simulate writes to a throwaway temp dir, cleaned after). No durable store is required
  until we add "save my generated processes" / history. Flag if you want that.

_(The earlier "vision unavailable" blocker was resolved mid-session when vision became available;
the UI was then visually verified and polished — see the Log.)_

## Log
- 2026-11-11 recon: model endpoint live (Qwen-instruct @ spark.local:4000, 262K ctx).
  Tooling: node 24, maven 3.9, java 25, docker running. ComposePane is a reusable
  Vue Flow graph editor. SkillPack is Jackson-serializable to the frontend ComposePack shape.
  RuntimeCore.ingest is isolated per call (platform.plus + unique-suffix agent) -> safe for simulate.
- Generation SPIKE (via /tmp/gen_spike.py, mirrors ProcessFlowGenerator): the model produced a
  clean 9-step GOAP flow from the TradingAgents docs — 4 parallel analyst steps (UserInput ->
  TechnicalReport/SentimentReport/NewsMacroReport/FundamentalsReport) -> research_debate ->
  investment_plan -> transaction_proposal -> risk_debate -> portfolio_manager_decision -> FinalDecision.
  Goal reachable, no dangling inputs, 9 types. Single call, ~20K tokens, ~40s. Generation quality is good.
- Built the backend (`/api/generate`, `/api/simulate`, `/api/discover` + DTOs + StudioConfiguration
  beans) and the frontend home workspace (ProcessWorkspace, router `/`, App nav "Studio" group,
  types.ts/client.ts). Typechecks and builds; Playwright E2E confirmed home loads, docs load (8),
  and generate renders 9 nodes.
- **Simulate bug found + fixed.** First simulate returned "skill source 'folder:...' contains no
  skill-packs". Root cause: the model emits *snake_case* prompt vars (`{{ user_input }}`,
  `{{ technical_report }}`), but the runtime contract (SkillPackValidator) binds the *decapitalized
  camelCase* input-type name (`{{ userInput }}`, `{{ technicalReport }}`). So every step failed
  `PROMPT_UNDECLARED_INPUT`, the pack failed `SkillPackValidator`, and `FolderSkillSource` excluded
  it -> empty pack set. **Fix:** added `ProcessFlowGenerator.canonicalizePromptVars` — a post-parse
  normalizer that rewrites each `{{var}}` to the canonical decapitalized form of its matching input
  type (robust to any casing/underscore form), plus strengthened rule 7 of `process_to_flow.jinja`.
  Verified by a new offline test (`ProcessFlowGeneratorTest`: the real model answer normalises to a
  pack that passes on-disk validation and is discoverable by `FolderSkillSource`).
- **Simulate now works end-to-end.** curl `/api/simulate` on a fresh generated pack: success=true,
  all 9 steps completed (per-step durations), real `FinalDecision` output (rating/summary/thesis).
  App log shows the full GOAP data dictionary + per-step LLM outputs. Playwright E2E re-run: generate
  -> 10 nodes / 16 edges in a clean layered layout -> simulate "succeeded" with 10 step rows.
- **Vision became available; visually verified and polished the flow.** Viewed the live screenshots
  and found two issues, both fixed:
  1. *Goal clipped off-screen.* The canvas was a fixed 530px-wide box with no fit-to-view, so the
     7-column flow (~1625px wide) overflowed and the goal + 4 steps were cut off to the right.
     **Fix:** made the canvas full-width, added a dot-grid background, a highlighted goal node, a
     "Fit view" button, native zoom/pan, and an automatic `fitView` after generation. Because Vue Flow
     measures node dimensions asynchronously, the auto-fit polls the flow's nodes until every one is
     measured before fitting (`fitWhenMeasured`). Verified: all 10 nodes on-screen, goal visible,
     0 overlaps, 16 edges.
  2. *Analysts stacked from the top, leaving empty space below.* **Fix:** `packToNodes` now centers
     each layer's rows on a common vertical midline, so the 4 analysts fan out symmetrically and the
     whole flow sits centered in the canvas.
  Re-ran the full E2E (load docs → generate → simulate): still green — 10 nodes / 16 edges,
  simulate "succeeded" with 10 step rows and teal-highlighted completed nodes. Frontend suite:
  36/36 pass; `vue-tsc --noEmit` clean.
