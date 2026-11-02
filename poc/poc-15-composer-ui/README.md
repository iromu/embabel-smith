# POC 15 — The visual skill-chain editor (the builder UI)

The **builder UI** that doc 01 names as the missing piece (*"Embabel already ships ~70% of the
pieces; the missing piece is the builder UI"*) and that doc 03 §2.2 sketches as a "visual
skill-chain editor". POC 14 proved the headless graph model this sits on (the type-checked canvas,
the composed chain, and the composite export). POC 15 adds the **pixels**: a real, browsable
drag-and-drop canvas that drives that same model over REST.

Fully offline and deterministic — no model endpoint. The canvas only manipulates skill-packs (YAML)
through POC 14's proven `CompositionCanvas` / `CompositeExporter`; it never calls a model.

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

## How it works

```mermaid
sequenceDiagram
    participant B as Browser canvas<br/>(static/index.html)
    participant C as CanvasController (REST)
    participant M as POC 14 model<br/>CompositionCanvas / CompositeExporter
    B ->> C: GET /api/skills
    C ->> M: load valid exported packs
    C -->> B: skill palette (draggable chips)
    B ->> C: POST /api/canvas (drag edge)
    C ->> M: validate() + chain()
    C -->> B: {valid, issues, chain} — blue/red wire
    B ->> C: POST /api/compose (Export)
    C ->> M: CompositeExporter
    C -->> B: composite skill-pack written
```

- **`ComposerUiApp`** — a `@SpringBootApplication` that boots the web app.
- **`SkillCatalog`** — the palette: loads every valid, exported skill-pack under the skill-packs
  directory into a `name → SkillNode` map (walks up from the working dir to find the repo's
  `skill-packs/` unless `builder.skill-packs-dir` is set).
- **`CanvasController`** — the REST surface; every endpoint delegates to POC 14's model:
  - `GET /api/skills` → the palette (`SkillInfo`).
  - `POST /api/canvas` → `{valid, issues, chain}` via `CompositionCanvas.validate()` / `chain()`.
  - `POST /api/compose` → writes the composite and reports its shape via `CompositeExporter`.
- **`static/index.html`** — the self-contained canvas (vanilla JS, no build step): a skill palette,
  a drag-and-drop stage, SVG wires, a live status badge, and Export / Reset controls.

## Run it

Offline (no endpoint). It's a web app — boot it, then open the canvas in a browser:

```bash
SERVER_PORT=18080 mvn -pl poc/poc-15-composer-ui spring-boot:run
# → open http://localhost:18080/
```

`SERVER_PORT` defaults to 18080 to avoid a local service on 8080; `COMPOSE_OUTPUT_DIR` (default
`target/composed-ui`) is where `POST /api/compose` writes exported composites.

## Tests

`CanvasControllerTest` pins the four REST behaviours offline — palette listing, valid-chain
acceptance, type-incompatible rejection, and composite export — using a standalone `MockMvc`
(spring-test) over a hand-built `SkillCatalog` and a temp export dir. It uses standalone `MockMvc`
rather than `@WebMvcTest` because the latter lives in the `spring-boot-webmvc-test` slice, which is
not present in the offline repo at this project's resolved Spring Boot version.

See `docs/04-poc-run-results.md`.
