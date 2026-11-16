# The Studio

`prod/embabel-smith-studio` is one web application over the runtime and builder: a Vue 3 SPA and a
Spring Boot REST surface. It holds **no capability of its own** — every controller delegates to a
proven class from the runtime or builder.

## Two rules the module is built on

**Thin controllers, cores do the work.** One `@RestController` per pane, roughly 30–60 lines,
delegating to `AgentSpecRunService`, `BuilderService`, the improvement loop, or the store.
Collaborators arrive as narrow functional interfaces, so a controller is testable offline with fakes
and no live model.

**Pure logic stays out of the views.** This is the load-bearing frontend decision: the POC's
single-file vanilla UI was untestable precisely because logic and DOM were fused. So the canvas snap
rule is a pure module, `src/api/client.ts` is the **single** fetch site (tests mock once), and the
DTO mirror in `src/api/types.ts` means a backend field change is caught by `vue-tsc`. The test
pyramid is four layers: `vue-tsc --noEmit` as the type gate, Vitest on pure logic,
Vitest + `@vue/test-utils` on components, and Spring Boot MockMvc per controller.

Vue 3 + Quasar was chosen because ten panes are mostly dashboard chrome, which Quasar collapses into
one-liners and supplies a sortable table and dark mode for free.

`src/main/resources/static/` is gitignored and produced by `npm run build`; the Vite dev server
proxies `/api` to the app, which avoids CORS. Committing `dist` was rejected in favour of a
documented build step.

## Surfaces

`/` is the **process workspace**: describe → generate → edit → dry-run → publish. The POC-era panes
are demoted to a secondary *Operations* group, because a 13-pane sidebar does not express the
product's primary purpose.

| Route | Pane | Backed by |
| --- | --- | --- |
| `/`, `/process` | ProcessWorkspace | `GenerateController`, `SimulateController`, `DiscoverController`, `ExtractController` |
| `/run` | RunPane | `RunController` |
| `/author` | AuthorPane | `BuildController` |
| `/compose` | ComposePane | `ComposeController` |
| `/eval` | EvalPane | `EvalController` |
| `/lifecycle` | LifecyclePane | pack lifecycle DTOs |
| `/security` | SecurityPane | `SecurityController` |
| `/trust` | TrustPane | `TrustController` |
| `/traces` | TracesPane | `TraceController` |
| `/improve` | AutoImprovePane | `ImproveController` |
| `/storage` | StoragePane | `StorageSettingsController`, `StoreController` |
| `/retrieve`, `/remote`, `/scale` | RetrievePane, RemotePane, ScalePane | **none — POC-only stubs** |

The last row is deliberate: all three are thin wrappers around `PocOnlyPane.vue`, which renders the
POC reference and a disabled badge. There is no retrieve, remote or scale controller under
`prod/embabel-smith-studio`. Retrieval-at-scale and the remote bridges are proven in the POCs and
**not wired into the product** — tracked in [`12-backlog.md`](12-backlog.md).

## The process workspace

- **One pack, not "skills + tools."** The no-code format has no LLM-tool concept, and *tool* already
  means a trust-tier group here. A second artifact type would be a generative process with no
  consumer.
- **The model proposes the step split.** The user's job is to describe, not decompose. Steps are
  wired by typed data: a step's output type is a later step's input, the first steps take
  `UserInput`, and the goal's output must be produced by some step.
- **Prompt-variable canonicalisation is a generator duty, not a validator relaxation.** Models
  habitually emit `{{technical_report}}`; the runtime contract binds the decapitalized input-type
  name (`{{technicalReport}}`), so the pack fails `PROMPT_UNDECLARED_INPUT`, is silently excluded by
  the folder source, and surfaces as "contains no agent specs". The generator rewrites each variable
  to the canonical form case- and underscore-insensitively and **leaves unrecognised variables
  untouched**, so a genuine error still fails.
- **Simulate is a throwaway run.** The pack is written to a fresh named subdirectory of a temp dir
  (the folder source scans immediate subdirectories), run on an isolated platform, then cleaned.
  `RuntimeCore.ingest` is isolated per call, so a dry-run cannot affect a real source.
- **The flow layout is derived, never stored** — a pure function of the pack (a node's column is one
  past the maximum of its producers, the goal is rightmost, edges come from type-name wiring). Edits
  re-derive it, so nothing stale persists.

## Degradation without a model

With no `OPENAI_CUSTOM_*` set, the application boots and every disk-backed pane works. Model-backed
actions return a structured `MODEL_NOT_CONFIGURED` that the UI renders as a banner — not a 500.

## Storage

Durable storage arrived after the file-backed v1, and its shape is entirely explained by one
requirement: **the endpoint must be rebindable at runtime**.

- **A programmatic `StorageContext`, with zero Spring-managed JPA beans.** The alternatives weighed
  were plain JDBC (cheaper, rejected) and restart-to-apply (rejected: the requirement is runtime).
  The consequence is that datasource, JPA and Spring Data auto-configurations are *excluded* so the
  app boots with no datasource at all — which also solves the "nothing configured yet" startup.
- **Rebind order:** build pool and entity-manager factory → verify connectivity → atomic swap →
  destroy the previous. A failed verification keeps the old binding and shows the connection error.
  `ProcessStore`'s public API never changed, so no controller ever sees a binding.
- **Settings live in a local document, not the database** — `~/.smith/studio/storage.json`, mode
  `0600`. Chicken-and-egg: the settings configure the database. Resolution order at startup is
  document → `smith.studio.storage.url` property → unbound.
- **A pack is stored as one serialized JSON blob, not one row per step.** Reconstructing a
  deployable directory from normalized rows is ceremony that duplicates structure the YAML already
  carries.
- **Traces are pluggable through the runtime's `TraceStore` SPI** — JSONL by default, the same
  database, or a separate one. The record shape is unchanged.
- **Tests need no Postgres.** An in-memory H2 is selected by a test-profile property so the
  full-context tests boot without Docker, and the rebind path is tested directly: bind `mem:one`,
  write, rebind `mem:two`, then assert the new one serves, the old database is untouched, and the
  old pool reports closed. Portability is enforced by using only column types both engines share
  (`TEXT`, `BIGINT`, `TIMESTAMP`) with `ddl-auto=update`; the H2 suite is the drift detector.

Two accepted risks, documented rather than fixed, because this is a local development tool: the
settings document holds the database password in plaintext, and a request mid-transaction on the old
binding can fail during a rebind (the old factory is destroyed after the swap). Both surface as clear
errors.

**Divergence from the original spec:** the spec said the *studio module* ships the development
compose file. It lives at the repository root, `docker/docker-compose-infra.yml`. The code is
authoritative; the difference is accepted.

## Predecessor: POC 22's unified UI

`poc/poc-22-unified-ui` proved that every proven capability could sit behind one browser surface with
ten panes over a thin REST layer, and established the patterns the studio kept verbatim — thin
controllers, the offline / endpoint-gated split, graceful "endpoint not configured" states, the
single API client. Only the delegates changed.

What the product dropped is the retrieve, remote and scale panes (above). The type-checked canvas
snap and the curation-aware retrieval survive only in the POC; the product's equivalent is the
Compose pane's editable flow.
