# Proposal

## Why

Deliverable 2's face: the combined runtime+builder+self-improvement product as a web app. POC 22 proved the unified builder UI (ten panes over the proven POC backends, Vue 3 + Quasar + Vite + TypeScript, 20/22 tasks done). This change turns that proof into the product: the same panes now drive the production cores (changes 03/04) instead of POC launchers, the self-improvement loop (change 05) gets its scheduler and control surface, and two new panes — Traces and Auto-improve — make the loop visible and operable.

## What Changes

- Complete `prod/embabel-smith-studio` (skeleton from change 01; loop logic from change 05):
  - A Spring Boot application hosting the production cores: run goals (change 03), build packs (change 04), operate the improvement loop (change 05).
  - A REST surface: run, build, list sources/packs, list traces, list/approve/reject pending candidates, trigger the loop on demand, start/stop the loop schedule, toggle auto-publish.
  - The frontend, evolved from POC 22's Vue 3 + Quasar + Vite + TypeScript SPA: the ten proven panes re-pointed at the production REST surface, plus a **Traces** pane (per-skill trace history, step-level drill-down) and an **Auto-improve** pane (loop status, pending candidates with evidence, approve/reject, auto-publish toggle, lifecycle states).
  - Loop scheduling: interval-based, off by default, started/stopped from the UI; on-demand trigger available.
- The frontend keeps POC 22's testable architecture (pure logic separated from views, `vue-tsc --noEmit`, Vitest).

## Capabilities

### New Capabilities

- `studio-web`: the unified web product — the REST surface over the production cores, the ten re-pointed panes plus Traces and Auto-improve, and the loop control (schedule, on-demand trigger, approval flow, auto-publish).

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-studio/` (application, REST controllers + DTOs, frontend under `frontend/`, tests, README).
- Depends on `embabel-smith-runtime`, `embabel-smith-builder`, `embabel-smith-skillsource` (skeleton POM from change 01).
- Sequencing: POC 22's two remaining verification tasks should land first — the studio frontend starts from POC 22's finished SPA.
- No POC module is modified; POC 22 remains frozen evidence once its tail tasks close.
