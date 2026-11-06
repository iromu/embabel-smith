# Proposal

## Why

POC 15 proved a builder UI exists, but it is a single vanilla-JS file whose logic is tangled into
the DOM — hard to test, hard to edit — and it drives only four of the twenty-one proven POC
capabilities. Doc 01 names the builder UI the "missing piece." This POC shows that piece can be a
**typed, component-based, unit-tested SPA** (Vue 3 + Quasar + TypeScript) that drives the *full*
set of proven capabilities over a thin REST surface, at a frontend footprint small enough that an
LLM can hold and edit it.

## What Changes

- Add a new POC module `poc/poc-22-unified-ui`: a Spring Boot web app plus a Vue 3 + Quasar +
  TypeScript SPA (Vite build, Vitest tests). POC 15 is left untouched as the focused skill-chain
  editor.
- The SPA exposes **10 panes**, each driving a proven POC capability:
  - **Run** — POC 1/4/5 (pick a skill, run the GOAP plan, see output)
  - **Author** — POC 2/3 (NL → draft + `DraftQuality`)
  - **Compose** — POC 14 (drag-drop canvas, type-checked "snap", export composite)
  - **Retrieve** — POC 8/12/17/19/20 (`SkillScorer` seam, POC-18 curation-aware)
  - **Eval** — POC 5/9 (score + prompt rewrite + re-score)
  - **Lifecycle** — POC 18 (drift detection, re-eval, state transitions)
  - **Security** — POC 21 (scan a pack → findings + `SAFE`/`REVIEW`/`BLOCK`)
  - **Trust** — POC 10 (tier + tool-group allowlist + guardrail)
  - **Remote** — POC 13/16 (MCP tool invoke / `RestAction` over HTTP)
  - **Scale** — POC 12/17/19/20 (shadowing `P@1` table + chart)
- Backend: **10 thin `@RestController`s**, each delegating to a proven POC class (the POC 15
  pattern). The default build depends only on the *light* POC modules; heavy scorers (POC 17/19/20)
  plug in via the existing `SkillScorer` seam, and the remote panes (POC 13/16) are gated on the
  sidecar/MCP server being reachable.
- Frontend architecture: **pure logic separated from the views** — `canvas/model.ts` (the
  type-checked snap rule, unit-tested), `api/client.ts` (the single `fetch` site), `types.ts` (the
  TypeScript ↔ backend DTO contract), and thin `.vue` panes.
- `static/` is **gitignored**; the SPA is built with `npm run build` into the Spring Boot static
  directory.

## Capabilities

### New Capabilities
- `unified-builder-ui`: the unified builder SPA plus its thin REST surface, which exposes the
  proven POC capabilities (run, author, compose, retrieve, eval, lifecycle, security, trust,
  remote, scale) to the browser, with offline / endpoint-gated / docker-gated behavior per pane.

### Modified Capabilities
<!-- None: `openspec list --specs` reports no existing main specs; this is a new capability. -->

## Impact

- **New module** `poc/poc-22-unified-ui` (Java + `frontend/`), registered in `poc/pom.xml`.
- **New REST endpoints** under `/api/*` (one per pane).
- **New frontend build** — Vite + Quasar + TypeScript + Vitest — introduced to the repo.
- **Dependencies** on existing POC modules: the light POCs by default; POC 17/19/20 (scorers) and
  POC 13/16 (remote) optional, wired via the `SkillScorer` seam and sidecar-gating respectively.
- **No changes** to existing POC modules — POC 15 remains the focused skill-chain editor.
