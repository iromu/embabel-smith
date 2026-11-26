# The Studio

`prod/embabel-smith-studio` is one web application over the runtime and builder: a Vue 3 SPA and a
Spring Boot REST surface. It holds **no capability of its own** — every controller delegates to a
proven class from the runtime or builder.

## Two rules the module is built on

**Thin controllers, cores do the work.** One `@RestController` per pane, most of them 50–105 lines,
delegating to `AgentSpecRunService`, `BuilderService`, the improvement loop, or the store.
`StoreController` (227 lines) is the outlier because pack download and import zipping live in it.
Collaborators arrive as narrow functional interfaces, so a controller is testable offline with fakes
and no live model.

**Pure logic stays out of the views.** This is the load-bearing frontend decision: the POC's
single-file vanilla UI was untestable precisely because logic and DOM were fused. So the flow-graph
rules are pure modules — `compose/model.ts` owns connection typing and the derived layout —
`src/api/client.ts` is the **single** fetch site (tests mock once), and the
DTO mirror in `src/api/types.ts` means a backend field change is caught by `vue-tsc`. The test
pyramid is four layers: `vue-tsc --noEmit` as the type gate, Vitest on pure logic,
Vitest + `@vue/test-utils` on components, and Spring Boot MockMvc per controller.

Vue 3 + Quasar was chosen because ten panes are mostly dashboard chrome, which Quasar collapses into
one-liners and supplies a sortable table and dark mode for free.

`src/main/resources/static/` is gitignored and produced by `npm run build`; the Vite dev server
proxies `/api` to the app, which avoids CORS. Committing `dist` was rejected in favour of a
documented build step.

## Surfaces

`/` is the **process workspace**: describe → generate → edit → dry-run → publish. The fourteen nav
items are spread over six groups — Studio, Run & Build, Inspect, Govern, Operate, Configure — with
the POC-era panes pushed down the list, because a flat fourteen-item sidebar does not express the
product's primary purpose. Retrieve sits under *Inspect*; Remote and Scale sit under *Operate*.

| Route | Pane | Backed by |
| --- | --- | --- |
| `/`, `/process` | ProcessWorkspace | `GenerateController`, `SimulateController`, `DiscoverController`, `ExtractController` |
| `/run` | RunPane | `RunController`, `SourceController` (the goal list) |
| `/author` | AuthorPane | `BuildController`, `SourceController` (the source list) |
| `/compose` | ComposePane | `ComposeController`, `SourceController` |
| `/eval` | EvalPane | `EvalController` |
| `/lifecycle` | LifecyclePane | `SourceController` (pack list) + the sidecar drift it reports |
| `/security` | SecurityPane | `SecurityController` |
| `/trust` | TrustPane | `TrustController` |
| `/traces` | TracesPane | `TraceController` |
| `/improve` | AutoImprovePane | `ImproveController` |
| `/storage` | StoragePane | `StorageSettingsController`, `StoreController` |
| `/retrieve` | RetrievePane | `RetrieveController`, `SourceController` (the source list) |
| `/remote` | RemotePane | `RemoteController` (the bridge client is the runtime's `RemoteActionBridge`) |
| `/scale` | ScalePane | **none — POC-only stub** |
| (chrome, all panes) | — | `StatusController`, `ModelsController` |

`SourceController` (`/api/sources`, `/api/packs`, `/api/goals`) backs five of the panes above rather
than owning one, which is why it appears in several rows.

The POC-only row is deliberate: the scale pane is a thin wrapper around `PocOnlyPane.vue`, which
renders the POC reference and a disabled badge, and there is no scale controller under
`prod/embabel-smith-studio`.

### Remote bridges are reported, never started

`/remote` lists the sidecars this installation configured, says whether each answers, shows the
actions each one advertises, and returns the output of running one of them. Three decisions shape it:

- **Sidecars are started by hand; the studio never spawns a process.** This was an open backlog
  question and is now closed as a decision. A browser-reachable, port-bound development tool that
  launches processes is a remote-code-execution surface, and spawning would make Docker a product
  dependency. So an absent sidecar is *reported*, with the URL that was probed, rather than launched,
  and the pane carries no "start" affordance to tempt a reader into thinking it could.
- **A request names a bridge; it never supplies an address.** `POST /api/remote/execute` carries the
  configured *name*, and a name `smith.studio.remote-bridges` does not define is refused before
  anything is contacted. Without that rule the pane is an open proxy: any client that can reach it
  could make this host send arbitrary requests to arbitrary URLs.
- **The controller holds no HTTP.** `RemoteController` reads configuration, selects a bridge and
  shapes DTOs; the three-endpoint contract, the timeouts and how a reply decides reachability all
  live in the runtime's `remote/` package — see
  [`07-runtime.md`](07-runtime.md). That split is enforced rather than stylistic:
  `NoDirectModelRequestTest` fails the build if any studio production source imports
  `java.net.http.` ([`05-architecture.md`](05-architecture.md) §the model layer).

An unreachable bridge is a *state* inside a `2xx` response, never a failed request: the pane reports
on the world, and one sidecar being down is not a fault in this application. The configured MCP
endpoint is displayed as configured and **not invoked** — this product is not an MCP client, so
showing it as reachable would be a badge that lies.

The scale pane stays POC-only by decision, and it is not the retrieval capability it is grouped with:
what it shows is a benchmark over a large synthetic library, whose measured P@1 lives in
[`04-poc-run-results.md`](04-poc-run-results.md) §7.1 — a harness result rather than something a pane
can offer a user. The retrieval capability that measurement recommended *is* now wired: `/retrieve`
ranks a source's packs through `RetrieveController`, and the ranking is curated by each pack's
recorded lifecycle state.

Two rules decide what that ranking can and cannot find, and both are the builder's, not the pane's.
The text a pack is matched on is built once by `retrieval/SpecEntries`: the pack's name, its goal
description, every step description, then its **external** input type names — every step input minus
the types the pack itself produces — and its goal output type. Name and descriptions carry the
meaning; the type names carry the plumbing a query rarely states but a planner keys on. A pack with no
descriptions still yields an entry, so an undocumented pack is rankable rather than invisible. And
`k` is bounded: `dto/RetrieveRequest` defaults it to 10 and caps it at 50, so a blank or
non-positive request asks for the default and a typo cannot ask for the whole library.

## The process workspace

- **Ingestion is the builder's.** Loading documents, assembling the corpus and extracting a
  description live in `prod/embabel-smith-builder`'s `blueprint/` package — see
  [`08-builder.md`](08-builder.md). `DiscoverController`, `ExtractController` and
  `GenerateController` delegate to those three stages and keep the stepwise interaction the UI
  depends on: each endpoint calls the one stage it is named for and never runs the next.
  `generate/FlowValidator` is the one class that stayed, because it is static, makes no model call and
  mirrors the Compose editor's client-side rule — UI validation, not build core.
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
actions return a structured `MODEL_NOT_CONFIGURED` that the UI renders as a banner — not a 500. The
message names the provider the environment resolved to and the variables still outstanding, so the
fix is stated rather than guessed.

Two endpoints tell the browser what the environment can run before a pane spends a run discovering
it. `/api/status` (`StatusController`) says whether an endpoint resolved and which model a request
naming none would use. `/api/models` (`ModelsController`) feeds the **model picker** every
model-launching pane offers: for an OpenAI-compatible endpoint that is the declared
`OPENAI_CUSTOM_MODELS` list, because a gateway that does not serve the bundled catalog would
otherwise offer names it cannot honour; for a catalog provider (OpenAI, Anthropic) it is the LLMs
Embabel registered.

**A request that names a model runs everything it does on that model** — generation, the eval run,
and any refinement. Every model-launching request DTO carries an optional `model`, and it is pinned
as `llm.model` onto the step specs staged for that request rather than onto the pack: the pack on
disk keeps exactly what it had, and a step that already named an `llm` keeps its other settings with
only the model replaced. A request naming none behaves as it always did: the picker is clearable, its
placeholder reads *Platform default*, and a cleared selection sends no `model` field at all.

## Loop settings

The `smith.studio.loop.*` properties, together with the source they publish to, are bound in
`StudioConfiguration` and decide what the Auto-improve pane is allowed to do on its own:

- `smith.studio.loop.auto-publish` (default `false`) — whether an iteration publishes the candidates
  it gates instead of holding them for approval. Toggleable at runtime from the pane.
- `smith.studio.loop.push` (default `false`) — whether a published candidate is pushed to the improve
  source's git remote. Deliberately **not** toggleable at runtime: pushing is the credential-and-intent
  decision the commit-never-push rule exists to protect. A `git:` improve source with pushing off is
  refused rather than committed into a clone the publisher deletes, and the candidate stays pending.
  The reasoning is in [`10-self-improvement.md`](10-self-improvement.md).
- `smith.studio.improve-source` (default empty) — the source the loop watches and, when it names one,
  where an approved candidate is written: a `folder:` or `git:` destination, falling back to the first
  configured `smith.studio.sources` entry. Empty with no sources
  configured, the loop **refuses to publish** and names this setting rather than defaulting
  the destination to the system temporary directory — a folder there is overwritten by the next
  iteration and may be discarded by the operating system, so a default would be a destination that
  cannot be relied on to still hold the improvement. The candidate stays pending. What the property does
  not decide is what the loop *reads*: with nothing set, `StudioConfiguration.improveSource` falls back
  to `BuilderPackSurface.specSource()` — the builder's pack sources in the checkout — so an
  unconfigured studio runs its loop over the packs that actually ship, and a studio started with no
  checkout fails at that bean rather than booting inert. An explicit value still wins for both readings.

## Storage

Durable storage arrived after the file-backed v1, and its shape is entirely explained by one
requirement: **the endpoint must be rebindable at runtime**.

- **A programmatic `StorageContext`, with zero Spring-managed JPA beans.** The alternatives weighed
  were plain JDBC (cheaper, rejected) and restart-to-apply (rejected: the requirement is runtime).
  The consequence is that datasource, JPA and Spring Data auto-configurations are *excluded* so the
  app boots with no datasource at all — which also solves the "nothing configured yet" startup.
- **Rebind order:** build the pool → run a test query to verify connectivity → build the
  entity-manager factory → atomic swap → destroy the previous. Verification happens *before* the
  factory exists, so a bad endpoint closes the half-built pool and throws, and the caller keeps its
  previous binding.
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
  old pool reports closed. Portability is enforced by using only column types both engines share:
  short `VARCHAR` for name and goal, an `INTEGER` step count, `TIMESTAMP` for the save time, and
  `LONGVARCHAR` for the pack body — chosen deliberately *over* `@Lob`, because a CLOB does not map
  cleanly across the two. `ddl-auto=update`; the H2 suite is the drift detector.

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

What the product dropped is the scale pane (above); remote and retrieve are wired. The type-checked
canvas snap survives only in the POC — the product's equivalent is the Compose pane's editable flow —
and so does the *scale benchmark*, while the curation-aware retrieval it proved now runs in the product
as the builder's `retrieval/` package behind `RetrieveController`.
