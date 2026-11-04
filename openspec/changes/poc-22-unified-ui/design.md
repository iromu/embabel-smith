# Design

## Context

POC 15 is the existing builder UI: a single vanilla-JS `static/index.html` served by a Spring Boot
app, with thin `@RestController`s that delegate to proven POC classes. It drives four capabilities
(compose, author, eval, retrieve) plus curation. Its weakness is the frontend — logic and DOM are
fused in one file, so it is hard to test or edit.

The repo holds twenty-one proven POCs. They split into:

- **Light** (fast, model-free or endpoint-gated): `poc-1` (run), `poc-2` (NL compile), `poc-9`
  (eval/refine), `poc-10` (trust), `poc-12` (retrieval), `poc-14` (composer), `poc-18` (lifecycle),
  `poc-21` (security), plus `smith-common`.
- **Heavy** (slow to build and/or need a running process): `poc-17` (GLiNER2/ONNX), `poc-19`
  (small-LLM/Docker), `poc-20` (ONNX embedding), `poc-13` (MCP server), `poc-16` (RestAction sidecar).

The `SkillScorer` seam is already shared by the retrieval POC family (POC 8/12/17/20) and is used by
POC 15 — lexical is the default, POC 20's embedding is a documented drop-in.

Node 24 + npm 11 are available; there is no existing frontend tooling in the repo (`site/` is the
GitHub Pages wiki). Port 8080 is held by an unrelated local service, so the app uses **18080**.

## Goals / Non-Goals

**Goals:**
- A typed (TypeScript), component-based, unit-tested Vue 3 + Quasar SPA that drives all ten
  capabilities (see proposal.md).
- Pure logic separated from the views, so the load-bearing rules are unit-testable and the code is
  small enough for an LLM to hold and edit.
- A thin REST surface that reuses the proven POC classes (the POC 15 pattern) — no new agent/skill
  logic.
- A default build that stays lean (light POCs only) while remaining capable of full coverage.

**Non-Goals:**
- Not a production product — a POC demonstrating the frontend architecture (claim C19).
- Not replacing POC 15, which stays the focused skill-chain editor.
- No new agent/skill behavior — the backend only wraps already-proven POC classes.
- No persistence of user state or workflow engine.

## Decisions

1. **New module `poc/poc-22-unified-ui`, not an extension of POC 15.**
   The project convention is one POC = one focused claim. Folding security/trust/remote into POC 15
   would change its identity and bloat its pom. *Alternative considered:* extend POC 15 — rejected
   (identity drift, POC 15's dependency list grows to nearly every module).

2. **Vue 3 + Quasar + Vite + TypeScript** (not plain Vue, not React).
   Ten panes are mostly *dashboard chrome* — header + nav drawer + cards + inputs + badges + tables
   + dialogs + dark theme — plus one custom canvas. Quasar collapses the chrome into one-liners
   (`q-layout`, `q-card`, `q-input`, `q-badge`, `q-table`) and gives the shell and a free sortable
   table and built-in dark mode. The Compose canvas is custom either way. *Alternatives considered:*
   plain Vue 3 (hand-roll every widget across ten panes — more repeated markup/CSS; rejected on
   chrome-savings grounds); React (rejected per user preference for Vue).

3. **Pure logic separated from the views.**
   - `frontend/src/canvas/model.ts` — the type-checked "snap" rule and chain validation as pure
     functions: `{nodes, edges, skills} → {valid, issues, chain}`. No DOM, no Vue.
   - `frontend/src/api/client.ts` — the **only** place that calls `fetch()`; every pane goes through
     it, so tests mock `fetch` once and exercise all panes' data flow.
   - `frontend/src/api/types.ts` — TypeScript interfaces mirroring the backend DTOs; the
     type-safety payoff (a backend field change is caught by `vue-tsc` / the tests).
   - `.vue` panes are thin views — inputs + a button + a results block; the logic is not in them.

   *Why this is the load-bearing decision:* the vanilla single-file is hard to test precisely because
   logic and DOM are fused. This separation is what makes "testable when editing" true — the snap
   rule becomes a trivially unit-testable pure function, and the API layer is mockable in one place.

4. **Thin REST controllers delegating to proven POC classes** (the POC 15 pattern).
   One `@RestController` per pane (~30–60 lines), each wrapping a proven POC class. No new
   agent/skill logic — the pixels drive the same logic the offline POCs prove.

5. **Lean default build; heavy scorers via the `SkillScorer` seam; remote sidecar-gated.**
   The default pom depends only on the light POC modules. POC 17/19/20 scorers are opt-in (add the
   dependency + wire the bean); the retrieve pane reports which scorer is active. Remote panes
   (POC 13/16) check sidecar/MCP reachability and present a "not running" state when down.
   *Why:* hard-depending on every POC makes the build slow and conflict-prone (e.g., two modules
   pulling different `onnxruntime`/Spring versions). *Alternative considered:* hard-depend on all —
   rejected (build bloat + version conflicts).

6. **`static/` gitignored; built with `npm run build` into the Spring Boot static dir; Vite dev
   server proxies `/api` → `:18080`.**
   Don't commit generated code (keeps the footprint low); the dev proxy avoids CORS. *Alternative
   considered:* commit the built `dist` (runnable without Node) — rejected in favor of a documented
   build step.

## Test strategy

```
+----------------------------------------------------------------+
|  vue-tsc --noEmit          (type gate)                         |
|  catches type errors across the frontend on edit; the          |
|  types.ts <-> backend DTO contract is enforced here            |
+----------------------------------------------------------------+
|  Vitest unit (fast, no DOM)                                    |
|   canvas/model.ts  -> edge type-check + chain validation       |
|   api/client.ts    -> request shaping + response parse (mock)  |
+----------------------------------------------------------------+
|  Vitest + @vue/test-utils (component)                          |
|   mount a pane, fill inputs, click, assert the API was called  |
|   and the result rendered  (2-3 representative panes)          |
+----------------------------------------------------------------+
|  Spring Boot MockMvc (backend)                                 |
|   one test per controller, mirroring POC 15                    |
+----------------------------------------------------------------+
```

## Risks / Trade-offs

- **[Heavy scorer deps bloat the build / version conflicts]** → default build excludes them; they
  are opt-in via the seam; pin versions when enabling one.
- **[Remote panes need a live sidecar/MCP server]** → gate on reachability, show "not running";
  optionally spawn the sidecar (POC 19 precedent: DevTools-managed Docker).
- **[Quasar's component API is large — a mis-used `q-*` prop/event is a subtle bug]** → keep panes
  thin, use a small set of well-known components, rely on `vue-tsc` + component tests to catch
  misuse.
- **[Two "run" paths (local GOAP vs remote) could confuse the UI]** → one Run pane for local GOAP;
  a separate Remote pane for the network transports (MCP / RestAction).
- **[A Node toolchain added to a Java repo]** → isolated under `frontend/`; documented build step;
  `static/` gitignored.
- **[Model-gated panes flake on endpoint availability]** → endpoint-gate with a clear message (POC
  15 precedent); offline panes are unaffected.

## Migration Plan

No migration — this is a new module. Register it in `poc/pom.xml`. Build: `cd frontend && npm
install && npm run build` (outputs to `src/main/resources/static/`). Run: `SERVER_PORT=18080 mvn
-pl poc/poc-22-unified-ui spring-boot:run`, then open `http://localhost:18080/`. Rollback: delete
the module and its `<module>` entry in `poc/pom.xml`.

## Open Questions

- **Spawn vs. manual sidecars** — whether the POC *spawns* the remote sidecars (POC 19-style
  DevTools) or requires them to be started manually for the demo. Deferrable: the Remote pane's
  gating is identical either way, so it does not change the spec or the task breakdown.
- **Scale pane visualization** — `q-table` vs. a small hand-rolled bar chart for the P@1 data.
  Deferrable and cosmetic.
