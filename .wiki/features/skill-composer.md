---
title: "Skill Composer (POC 14)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-14-composer/src/main/java/com/embabel/smith/composer/SkillNode.java"
  - "poc/poc-14-composer/src/main/java/com/embabel/smith/composer/CompositionCanvas.java"
  - "poc/poc-14-composer/src/main/java/com/embabel/smith/composer/CompositeExporter.java"
  - "poc/poc-14-composer/src/main/java/com/embabel/smith/composer/ComposerApp.java"
  - "poc/poc-15-composer-ui/src/main/java/com/embabel/smith/ui/SkillCatalog.java"
  - "poc/poc-15-composer-ui/src/main/java/com/embabel/smith/ui/SkillCuration.java"
  - "poc/poc-15-composer-ui/src/main/java/com/embabel/smith/ui/CurationController.java"
  - "poc/poc-15-composer-ui/src/main/java/com/embabel/smith/ui/RetrievalController.java"
  - "poc/poc-15-composer-ui/src/main/resources/static/index.html"
updated_at: "2026-11-03"
---

# Skill Composer (POC 14)

POC 14 is the **Skill Composer** that doc 03 §2.2 asks for: chain exported goals (skills) into a
workflow and export the result as a **new composite skill** — "an exported goal whose action chain
*is* the plan". It is built as a **headless, offline** model of the "visual skill-chain editor": the
drag-and-drop pixels are a thin frontend over this graph model, and the substance (the canvas, the
type-checked connections, the composed chain, and the composite export) is what is proven here.
Complements [[skill-composition]] (POC 6), which *runs* a composition at runtime; POC 14 *authors*
one as a durable pack.

## The canvas model

- **`SkillNode`** — one skill (an exported goal + its action steps). Its **output** is the exported
  goal's output type; its **external inputs** are the action input types it does not produce itself
  (for a single-action skill, just that action's input type).
- **`CompositionCanvas`** — the graph: skills + directed edges. An edge (A → B) is valid only when
  A's output type is one of B's external inputs — the drag-and-drop **"snap"**. `validate()` reports
  type-incompatible edges, a missing entry/exit, and structural problems; `chain()` returns the
  ordered entry→exit path (the composed plan). A valid canvas has exactly one entry skill (input
  `UserInput`) and one exit skill (output `BackgroundMessage`), with every intermediate edge
  type-compatible and the path covering every skill.

## Composite export

**`CompositeExporter`** builds the composite `SkillPack`: the **union** of the component skills'
action steps and intermediate types, under **one new exported goal** whose output is the exit
skill's output type. The component skills' own goals are dropped — only their typed actions matter,
and the non-LLM GOAP planner re-derives the chain from those actions' pre/postconditions. The result
is a plain, loadable skill-pack (see [[skill-pack-format]]); `SkillPackWriter` writes it to disk.

## How the run works

`ComposerApp` (offline, `mvn -pl poc/poc-14-composer exec:java`): loads `compose-extract`
(`UserInput → TopicBrief`) and `compose-explain` (`TopicBrief → BackgroundMessage`), builds the
canvas, validates the edge, prints the composed chain, exports the composite to
`target/composed/compose-composite`, and re-reads + re-validates it. Paths are overridable via
`COMPOSE_PACK_A` / `COMPOSE_PACK_B` / `COMPOSE_NAME` / `COMPOSE_OUTPUT_DIR`.

The exported composite then runs **live** through the no-code runtime (POC 1): the GOAP planner
chains `extractTopic → writeExplainer` from the single pack (goal achieved in ~12.5 s) and returns a
coherent goal output. See [[runtime-run]] and `docs/04-poc-run-results.md` §5.

## The visual UI (POC 15)

POC 15 (`poc/poc-15-composer-ui`) is the **builder UI** doc 01 names as the missing piece: a Spring
Boot web app that serves a self-contained browser canvas (vanilla JS, no build step) driving this
exact model over REST. The canvas, palette, and retrieval pane are offline; the author,
eval/refinement, and curate panes are endpoint-gated (a missing endpoint returns a clear, named
config error before any model call).

- **`SkillCatalog`** — the palette: every valid, exported skill-pack under `skill-packs/` as a
  `name → SkillNode` map (walks up from the working dir to find the repo's `skill-packs/`, or
  `builder.skill-packs-dir` if set).
- **`CanvasController`** — the REST surface; every endpoint delegates to POC 14's model:
  `GET /api/skills` (the palette), `POST /api/canvas` (`{valid, issues, chain}` via
  `CompositionCanvas`), and `POST /api/compose` (writes the composite via `CompositeExporter`).
- **`RetrievalController`** — the retrieval pane: `POST /api/retrieve` ranks the palette by
  relevance to a natural-language query via the same pluggable `SkillScorer` seam POC 8/12/17/20
  share (lexical by default, the POC 20 embedding a drop-in) — and **curates the library with POC
  18**: each hit carries the skill's lifecycle state, a retired skill is dropped, and a degraded one
  is de-ranked (score halved).
- **`SkillCuration`** — the library curation (offline): seeds each skill as an `ACTIVE` baseline
  (its live content hash, an assumed-passing score) and re-derives its lifecycle via POC 18's
  `LifecycleManager` from the recorded state, the live content hash, and the latest measured
  pass-rate — retirement is terminal, promotion is from incubation.
- **`NlAuthoringController`** — the author pane (endpoint-gated): `POST /api/compile` compiles a
  description into a draft via POC 2's `NlCompileRunner` and shows its goal shape + `DraftQuality`
  verdict.
- **`EvalController`** — the eval/refinement pane (endpoint-gated): `POST /api/eval` runs a POC 5
  eval over tasks against a picked skill and, on failure, offers a POC 9 prompt rewrite + re-score.
- **`CurationController`** — the curate re-eval (endpoint-gated): `POST /api/curate` re-scores a
  skill's live pass-rate with POC 18's `LiveEvalRunner` and moves it through the lifecycle,
  returning the before → after transition, the two drift signals, and POC 18's verdict.
- **`static/index.html`** — the canvas: a skill palette, a drag-and-drop stage, SVG wires, and a
  live status badge, plus a right-hand column with the Author, Eval/refine, and Retrieval panes
  (each retrieval hit shows a colored lifecycle badge + a "re-eval + curate" affordance). A
  connection "snaps" (blue) only when `/api/canvas` reports it type-compatible, and renders red
  with the reason otherwise; the chain and validation recompute on every move, and an Export button
  writes the composite pack.

Run: `SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run`, then open
`http://localhost:18080/` (the author / eval / curate panes also need the `OPENAI_CUSTOM_*`
endpoint env vars). Offline, eighteen standalone-`MockMvc` tests (`CanvasControllerTest`,
`RetrievalControllerTest`, `NlAuthoringControllerTest`, `EvalControllerTest`,
`CurationControllerTest`) pin the REST behaviours, and `SkillCurationTest` pins the pure curation
state machine. See `docs/04-poc-run-results.md` §5.

## What it proves

That skills can be composed *by design* (not just at runtime) and the result is a first-class,
portable skill-pack. Offline, `CompositionCanvasTest` pins the edge type-check / entry-exit /
disconnected-node cases and `CompositeExporterTest` pins the composite's shape and clean
re-validation. See [[what-the-pocs-prove]].
