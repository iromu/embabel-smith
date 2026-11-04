# POC 15 — The visual skill-chain editor (the builder UI)

The **builder UI** that doc 01 names as the missing piece (*"Embabel already ships ~70% of the
pieces; the missing piece is the builder UI"*) and that doc 03 §2.2 sketches as a "visual
skill-chain editor". POC 14 proved the headless graph model this sits on (the type-checked canvas,
the composed chain, and the composite export). POC 15 adds the **pixels**: a real, browsable
drag-and-drop canvas that drives that same model over REST.

The canvas, palette, and retrieval pane are fully offline and deterministic — no model endpoint.
The **author pane** and the **eval/refinement pane** are the endpoint-gated halves: the author pane
compiles a natural-language description into a draft via POC 2's `NlCompileRunner` (needs a live
OpenAI-compatible endpoint) and reports the draft's goal shape + `DraftQuality` verdict; the
eval/refinement pane runs a POC 5 eval over a set of tasks against a picked skill and, when tasks
fail, offers a POC 9-style prompt rewrite + re-score. Both return a clear configuration error rather
than an opaque model failure when no endpoint is set.

On top of the retrieval pane's raw relevance ranking, the canvas applies **POC 18's library
curation** (offline): each hit carries the skill's lifecycle state (incubating → active → degraded →
retired, driven by POC 18's `LifecycleManager` from the recorded baseline, the live content hash, and
the latest measured pass-rate), a retired skill is dropped from the results entirely, and a degraded
one is de-ranked. A **curate re-eval** (endpoint-gated) re-scores a skill with POC 18's
`LiveEvalRunner` and moves it through the lifecycle — so the library is curated, not just scored.

## What it proves

- **A browser canvas loads the skill palette.** `GET /api/skills` returns every valid, exported
  skill-pack under the skill-packs directory as a draggable chip (name, external input types, output
  type).
- **Connections "snap" only when the types match.** Dragging an output port onto an input port posts
  `POST /api/canvas`; a type-compatible edge renders blue, a type-incompatible one renders red with
  the reason (e.g. *"'BackgroundMessage' is not one of compose-extract's inputs [UserInput]"*). This
  is POC 14's edge type-check, surfaced visually.
- **The chain live-validates.** Every move recomputes `validate()` + `chain()`; the status badge
  shows "valid chain" with the computed path, or the list of issues.
- **Export writes a composite skill-pack.** The Export button posts `POST /api/compose`, which
  delegates to `CompositeExporter` and writes a well-formed composite pack (goal, steps, types) to
  the output dir — the same pack POC 14 proves runs end-to-end.
- **An author pane compiles a description into a draft.** The top right-hand panel posts
  `POST /api/compile` with a natural-language description; the draft's goal shape, its actions, and
  the `DraftQuality` verdict (HEALTHY / degenerate, with the reason) are shown, alongside any issues
  to review. This is the "describe a skill in plain language" half of the builder UI — POC 2's
  `NlCompileRunner` + POC 2/3's `DraftQuality` classifier, surfaced in the canvas. Endpoint-gated.
- **A retrieval pane shows which skills a task would route to.** The right-hand panel posts
  `POST /api/retrieve` with a natural-language query; the catalog is ranked by relevance and the
  top-k shown with their scores. It reuses the same pluggable `SkillScorer` seam the whole retrieval
  POC family shares (POC 8 lexical, POC 12 dense, POC 17 GLiNER2, POC 20 real embedding), so the
  offline lexical scorer is the default and a denser scorer is a drop-in.
- **The retrieval pane curates the library, not just scores it (POC 18).** Each hit carries the
  skill's lifecycle state, re-derived offline by POC 18's `LifecycleManager` from the recorded
  baseline, the live content hash, and the latest measured pass-rate. A **retired** skill is dropped
  from the results entirely (out of the library); a **degraded** one is de-ranked (its relevance
  score halved, so it falls in the ranking). This is the "shadowing/near-duplicate skills are
  curated rather than just scored" behaviour — the pure content-hash / drift / state-machine half is
  deterministic and offline.
- **A curate re-eval moves a skill through its lifecycle (endpoint-gated).** The right-hand panel's
  "re-eval + curate" posts `POST /api/curate` with a skill; it re-scores the skill's live eval
  pass-rate via POC 18's `LiveEvalRunner`, records it, and moves the skill through the lifecycle —
  reporting the before → after transition, the two drift signals, and POC 18's human-readable
  verdict. A clean re-eval keeps it active, a score drop degrades it, a score collapse retires it
  (and it then disappears from retrieval).
- **An eval/refinement pane checks and refines a skill in one place.** The right-hand panel posts
  `POST /api/eval` with a skill from the palette; it runs a POC 5 eval over a set of tasks and shows
  the composite score + the failing inputs, and (the stretch) when tasks fail it runs a POC 9-style
  prompt rewrite from the failures and re-scores, showing the before/after score and the revised
  prompt. This is POC 9's `RefinementRunner` (deploy → evaluate → rewrite → re-validate → redeploy →
  re-evaluate) surfaced in the canvas. Endpoint-gated.

## How it works

```mermaid
sequenceDiagram
    participant B as Browser canvas<br/>(static/index.html)
    participant C as CanvasController (REST)
    participant A as NlAuthoringController (REST)
    participant E as EvalController (REST)
    participant R as RetrievalController (REST)
    participant M as POC 14 model<br/>CompositionCanvas / CompositeExporter
    participant S as POC 12 ScaleSkillRegistry<br/>(pluggable SkillScorer)
    participant L as POC 2 NlCompileRunner<br/>(LLM, endpoint-gated)
    participant F as POC 9 RefinementRunner<br/>(deploy + eval + rewrite, endpoint-gated)
    participant CU as CurationController (REST)
    participant LC as POC 18 LifecycleManager<br/>+ SkillCuration (offline)
    participant LE as POC 18 LiveEvalRunner<br/>(deploy + eval, endpoint-gated)
    B ->> C: GET /api/skills
    C ->> M: load valid exported packs
    C -->> B: skill palette (draggable chips)
    B ->> C: POST /api/canvas (drag edge)
    C ->> M: validate() + chain()
    C -->> B: {valid, issues, chain} — blue/red wire
    B ->> C: POST /api/compose (Export)
    C ->> M: CompositeExporter
    C -->> B: composite skill-pack written
    B ->> A: POST /api/compile (description)
    A ->> L: compileAgent(name, description)
    L -->> A: AgentCompilationResult (goal + actions + issues)
    A -->> B: {goal, actions, DraftQuality verdict, issues}
    B ->> E: POST /api/eval (skill + tasks)
    E ->> F: improve(pack, step, tasks, endpoint)
    F -->> E: RefinementOutcome (before/after score, failing inputs, revised prompt)
    E -->> B: {score, failing inputs, revised prompt}
    B ->> R: POST /api/retrieve (query)
    R ->> S: retrieve(query, all) over catalog entries
    S -->> R: ranked SkillEntry (by scorer)
    R ->> LC: currentState(name) per hit
    LC -->> R: drop RETIRED, de-rank DEGRADED
    R -->> B: {scorer, results:[{name, score, state}]}
    B ->> CU: POST /api/curate (skill)
    CU ->> LE: evaluate(pack, tasks)
    LE -->> CU: live pass-rate
    CU ->> LC: curate(name, score) — move through lifecycle
    LC -->> CU: before → after state
    CU -->> B: {before, after, drift, verdict, surfaced}
```

- **`ComposerUiApp`** — a `@SpringBootApplication` that boots the web app.
- **`SkillCatalog`** — the palette: loads every valid, exported skill-pack under the skill-packs
  directory into a `name → SkillNode` map (walks up from the working dir to find the repo's
  `skill-packs/` unless `builder.skill-packs-dir` is set). Also exposes `retrievalEntries()`, which
  maps each skill to POC 8's `SkillEntry` (name + a description synthesized from the goal/step
  descriptions + I/O types) — the metadata the retrieval pre-filter scores against.
- **`CanvasController`** — the REST surface; every endpoint delegates to POC 14's model:
  - `GET /api/skills` → the palette (`SkillInfo`).
  - `POST /api/canvas` → `{valid, issues, chain}` via `CompositionCanvas.validate()` / `chain()`.
  - `POST /api/compose` → writes the composite and reports its shape via `CompositeExporter`.
- **`SkillCuration`** — the library curation (offline): seeds each catalog skill as a validated
  `ACTIVE` baseline (its live content hash, an assumed-passing score of 1.0), and re-derives each
  skill's lifecycle state by POC 18's `LifecycleManager.transition()` from the recorded state, the
  live content hash (content drift) and the latest measured score (score drift). `curate(name,
  score)` records a freshly-measured pass-rate and persists the new state (so retirement is terminal
  and promotion is from incubation, as in POC 18). It exposes `surfaced(state)` (a retired skill is
  out of the library) and `deRankFactor(state)` (a degraded skill's score is halved).
- **`RetrievalController`** — the retrieval pane: `POST /api/retrieve` ranks `catalog.retrievalEntries()`
  with a pluggable `SkillScorer` (default POC 8's `LexicalScorer`, offline) via POC 12's
  `ScaleSkillRegistry`, then applies POC 18's curation — dropping retired skills, de-ranking degraded
  ones, and stamping each hit with its lifecycle state — before returning the top-k. The scorer is a
  constructor field, so POC 20's real `EmbeddingService` drops in here without touching the endpoint.
- **`CurationController`** — the curate re-eval (endpoint-gated): `POST /api/curate` takes a skill
  from the palette and re-scores its live eval pass-rate with POC 18's `LiveEvalRunner`, records it
  via `SkillCuration.curate()`, and returns the before → after transition, the two drift signals,
  POC 18's `DriftReport` verdict, and whether the skill is still surfaced. It checks
  `ModelEndpointConfig.configError()` first, so a missing endpoint yields a clear, named error before
  any model call. (The re-eval deploys the skill as a throwaway agent, so — as in POC 18 — it runs
  skills whose actions use the built-in `UserInput` / `BackgroundMessage` types.)
- **`NlAuthoringController`** — the author pane: `POST /api/compile` compiles a description via POC 2's
  `NlCompileRunner` and maps the `AgentCompilationResult` to the draft's goal shape, actions, and the
  `DraftQuality` verdict. It checks `ModelEndpointConfig.configError()` first, so a missing endpoint
  yields a clear, named error before any LLM call (endpoint-gated).
- **`EvalController`** — the eval/refinement pane: `POST /api/eval` takes a skill from the palette and
  a set of tasks (a default set if none are given), then delegates the whole loop to POC 9's
  `RefinementRunner.improve()` — deploy the skill, evaluate, rewrite the prompt from the failing tasks,
  re-validate, redeploy, and re-evaluate. It checks `ModelEndpointConfig.configError()` first, so a
  missing endpoint yields a clear, named error before any model call (endpoint-gated).
- **`static/index.html`** — the self-contained canvas (vanilla JS, no build step): a skill palette,
  a drag-and-drop stage, SVG wires, a live status badge, Export / Reset controls, and a right-hand
  column with the Author, Eval/refine, and Retrieval panes.

## Run it

It's a web app — boot it, then open the canvas in a browser. The canvas, palette, and retrieval
pane (including its POC 18 curation) are offline (no endpoint); the **author**, **eval/refinement**,
and **curate re-eval** panes need the endpoint env vars:

```bash
SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run
# → open http://localhost:18080/

# for the author + eval/refinement panes (endpoint-gated), also set:
export OPENAI_CUSTOM_BASE_URL=... OPENAI_CUSTOM_API_KEY=...
export OPENAI_CUSTOM_MODELS=... EMBABEL_MODELS_DEFAULT_LLM=...
```

`SERVER_PORT` defaults to 18080 to avoid a local service on 8080; `COMPOSE_OUTPUT_DIR` (default
`target/composed-ui`) is where `POST /api/compose` writes exported composites.

## Tests

`CanvasControllerTest` pins the four REST behaviours offline — palette listing, valid-chain
acceptance, type-incompatible rejection, and composite export — using a standalone `MockMvc`
(spring-test) over a hand-built `SkillCatalog` and a temp export dir. It uses standalone `MockMvc`
rather than `@WebMvcTest` because the latter lives in the `spring-boot-webmvc-test` slice, which is
not present in the offline repo at this project's resolved Spring Boot version.

`RetrievalControllerTest` pins the retrieval pane offline — an extract query ranks the extract skill
first, an explain query ranks an explain skill first, results come back sorted by score and bounded
by `k`, every hit carries a lifecycle state, a **retired** skill is dropped from the results, and a
**degraded** skill's score is de-ranked — the same standalone `MockMvc` + hand-built `SkillCatalog`
and `SkillCuration`, with the default lexical scorer (no model).

`SkillCurationTest` pins the pure curation state machine offline (POC 18's `LifecycleManager`, no
model — the scores are plain inputs): a fresh catalog skill is active and surfaced, a clean re-eval
keeps it active, a score drop degrades it (and de-ranks it), a score collapse retires it out of the
library, retirement is terminal, and the de-rank factor only touches degraded skills.

`CurationControllerTest` pins the curate re-eval's offline scaffolding — no skill selected, an unknown
skill, and a known skill without an endpoint (clear, named configuration error) — built with no
`AgentPlatform` (the offline paths return before any model call).

`NlAuthoringControllerTest` pins the author pane's offline scaffolding — a blank description is
rejected, and a missing endpoint returns a clear, named configuration error before any LLM call —
built with no `Ai`/`AgentPlatform` (the offline paths never reach the model).

`EvalControllerTest` pins the eval/refinement pane's offline scaffolding — no skill selected, an
unknown skill, and a known skill without an endpoint (clear, named configuration error) — built with
no `AgentPlatform` (the offline paths return before any model call).

## Conclusion

Against the hypothesis (claim C15 — "the builder UI is the missing piece"): **proven.** A real,
browsable drag-and-drop canvas loads the skill palette, snaps only type-compatible connections,
live-validates the chain, and exports the composite skill-pack over REST — the same model POC 14
proves runs end-to-end. Every pane delegates to a proven POC, so the pixels drive proven logic:
the **retrieval pane** (the POC 8/12/17/20 `SkillScorer` seam, POC 18 curation-aware — live: a
score-collapsed skill was retired and dropped from results, a half-passing one de-ranked), the
**author pane** (POC 2 — live: compiled a description to a draft and surfaced the `DraftQuality`
verdict, which caught a `VOID_GOAL` degenerate draft on one run), the **eval/refinement pane**
(POC 5/9 — live: 0.0 → 1.0 on a deliberately-failing task), and the **curate re-eval** (POC 18 —
endpoint-gated). The honest limit of the live halves: a curate re-eval scores *behaviour*, so its
verdict is only as stable as the model's in-domain adherence (the same out-of-domain re-eval
retired a skill in one run and held it `STABLE` in the next).

See `docs/04-poc-run-results.md`.
