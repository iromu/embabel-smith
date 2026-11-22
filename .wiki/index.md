---
title: "Embabel Smith Wiki"
type: "index"
status: "active"
language: "default"
last_commit: "1b95d476b192044d664e6472f94be0a0647416b2"
updated_at: "2026-11-22"
---

<!-- Scoped ingest 2026-11-22 (the studio's Retrieve and Remote panes): two OpenSpec changes landed the
last two POC-only panes — `studio-retrieval-curation` and `studio-remote-bridges`. This ingest is scoped
to what they shipped, so `last_commit` is deliberately unchanged: 30 of the 40 files changed since it
belong to the builder's pack flow and the POC 27 work, not to these panes.
[[smith-studio-web]] gained "Retrieve and Remote: two panes whose logic lives elsewhere" for the parts no
controller shows — the curation rules are POC 18's two (`retrieval/RetrievalCuration.java` drops a
`retired` pack and halves a `degraded` one, then re-orders, because a halved score can sink below the pack
that had ranked under it); scoring sits behind a one-method `SpecScorer` seam with `LexicalScorer` the only
implementation that ships; the ranking, entry-shaping and curation arithmetic all live in the *builder's*
`retrieval/` package and `RetrieveController` holds none of it; and a request names a bridge rather than
supplying an address, which is what keeps `/remote` from being an open proxy. One backlog claim needed
correcting against the code: `docs/12-backlog.md` says "every response names the active scorer", but
`dto/RetrieveResponse.java` documents that field as null on failure, so the page says every *successful*
response does. [[smith-runtime]] gained the four remote shape classes in its `source_paths` plus the two
settings that are fixed rather than configured (3 s to connect, 10 s for the request) and why a
`ConnectException`'s own name is the reason kept. Lint after the edit: 45 pages, 0 broken cross-links, 0
orphans, 0 stale, 0 `source_paths` that do not exist. Republish to GitHub Pages is still owed — it needs a
push, and this repo does not push. -->

<!-- Scoped ingest 2026-11-22 (the builder's pack flow): `smith build` gained `--flow java|pack`, so the
builder's own `builder-spec-one-shot` pack can issue the single generation call — `cli/BuildFlow` plus the
one branch in `BuildCommand.generators()`, with `--flow pack --technique embabel` refused before any model
call.
[[smith-builder]] gained a "The pack flow" section for the three facts that are not visible from the flag:
the goal output arrives as a property map that `eval/AgentSpecInvokerFactory` must read the bound string
out of (the crash class that made the pack flow build nothing on 2026-11-22), the reply envelope
`builder-spec-one-shot/steps/draftAgentSpec.yml` states because nothing validates prose about output shape,
and the re-measured live verdict — pack builds 1 of 3 against java 2 of 3, so the flip's decision stands
and the flow stays non-default. Its falsified clause ("the other two blueprint inputs are being
re-measured") was replaced with the landed numbers, and this page gained the §7 envelope rule and an §8
row. `last_commit` is deliberately NOT advanced: 62 tracked files changed since it and this pass covered
the builder's pack-flow surface only, not POC 27's new headroom tasks, its README reframe, or the
studio→builder test moves. Lint clean: 45 pages, 0 broken links, 0 orphans, 0 stale, 0 dead source paths.
GitHub Pages re-publish remains OWED and was not attempted here. -->

<!-- Full ingest 2026-11-22: every tracked file changed since the previous `last_commit` (1311 of them
at this HEAD) was partitioned and surveyed — builder (305), studio (194), runtime + build/CI (86),
docs/POC evidence (717), root (5) — and 24 pages were refreshed against the code. What moved: the
runtime gained a retired-pack gate, per-run model pinning and a remote-action client; the builder gained
`--docs`/`--extract`, `smith blueprint`, validators exposed as `@LlmTool`s, its own four packs under
`builder-agent-specs/`, and the improvement engine (`builder/improve/`); the studio's loop now only
drives that engine; `ModelEndpointConfig` detects three providers, not one; CI exists
(`offline-verification.yml`) and runs the Makefile targets, not `mvn -q verify`; the groupId is now
`org.iromu.embabel.smith`; POC 27 landed with a *not proven* verdict. All seven dead `openspec/...`
references were repointed to `docs/`, since the planning trees were consolidated on 2026-11-15 and the
nine specs now in `openspec/specs/` were promoted by the 2026-11-21 archives. Lint clean: 45 pages,
0 broken links, 0 orphans, 0 dead source paths. GitHub Pages re-publish remains OWED: `wiki-pages.yml`
fires on pushes to `main` touching `.wiki/**`, and these edits sit on `poc/iteration-2` — until they
merge, https://iromu.github.io/embabel-smith/ still shows the pre-A2A wiki (`gh workflow run` is the
alternative). Known code/doc drift this ingest found but did not touch: `docs/07-runtime.md` still
places the `TraceStore` SPI in the runtime module, `docs/12-backlog.md:14` claims the nine promoted
specs restate rules `docs/` already states (two do not), `prod/embabel-smith-runtime-a2a/README.md:67`
claims the card and the run gate cannot disagree (the card is not lifecycle-aware), `LoopService`'s
javadoc links a `SelfImprovementLoop` class that no longer exists, and `offline-verification.yml`'s
header says four `@Tag("live")` tests where the tree has nine. -->

<!-- Scoped ingest 2026-11-21: the self-improvement engine moved from `studio/improve` into
`builder/improve`, so [[smith-studio]] and [[smith-builder]] were repointed and the module map below
corrected. `last_commit` is again deliberately NOT advanced — the full ingest is still owed, and the
GitHub Pages re-publish this file already records as owed is still owed. -->

<!-- Scoped ingests 2026-11-19: the A2A surface ([[a2a-endpoint]], [[smith-runtime]], [[build-tooling]],
[[model-calls-go-through-ai]]) and the skill `inspect` gate ([[skill-output-target]], [[smith-builder]]).
`last_commit` is deliberately NOT advanced — 1083 tracked files changed since it, so a full ingest is
still owed and the rest of these pages are unrefreshed since 2026-11-15. Lint ran clean (45 pages, 0
broken links, 0 orphans, 0 stale). GitHub Pages re-publish is OWED: `wiki-pages.yml` fires on pushes to
`main` touching `.wiki/**`, and these edits are on `poc/iteration-2`, so until they merge the site at
https://iromu.github.io/embabel-smith/ still shows the pre-A2A wiki (manual `gh workflow run` is the
alternative). -->

<!-- Scoped edit 2026-11-20: [[smith-studio-storage]] — the `packs` table's key is `(name, version)`,
not the bare name, and `PacksKeyMigration` re-keys a deployed name-keyed table at bind time.
`last_commit` again deliberately NOT advanced. Lint re-ran clean over all 45 pages (0 broken links, 0
orphans, 0 stale). GitHub Pages re-publish is still OWED for the same reason. -->

# Embabel Smith

A **GOAP-based agent builder** on the Embabel platform (v1.5.4, Java 21). One part *builds* a
spec; the other part *runs* one. The POCs under `poc/` prove the idea end to end; the product
modules under `prod/` ([[product-phase]]) turn that proven logic into a working builder and runtime.
This wiki explains how the pieces fit so a newcomer can orient fast and verify claims in code.

## 1. What is this?

Embabel Smith is a greenfield, multi-module Maven proof-of-concept (POC) that proves one idea end
to end: **a builder agent can turn a natural-language description into a deployable, no-code
"agent spec", and a fixed runtime can ingest that agent spec and run it — with zero compilation.**

An **agent spec** is a small directory of pure YAML (no code, no build step). It is the unit of
capability that the builder emits and the runtime ingests. See [[agent-spec-format]].

## 2. Get started

- **Build & test everything (offline):** `mvn -q verify` — that is the **default reactor**
  (`smith-common` + `prod/`); the POCs sit behind `-Ppoc` / `POC=1`. Tests pass with no model endpoint
  because the model-touching ones carry the `live` tag, which the build excludes by default.
- **Prefer the Makefile targets** (`make build`, `make test`, `make test-module MODULE=…`, `make lint`)
  — those are what CI runs. `make test` sources `~/.config/env/llm.env` first, but the tag exclusion
  still applies, so **`make test-live` is the only opt-in** that runs the endpoint-gated tests
  ([[build-tooling]]).
- **Once per clone:** `make setup` installs the pre-commit hooks (gitleaks, whitespace, `make lint`).
  Without it, a fresh clone commits unchecked.
- **Run a POC:** `mvn install -DskipTests` once (so sibling modules resolve), then run one module —
  with `-Ppoc`, since the POCs are outside the default reactor and `-pl poc/...` matches nothing
  without it:
  `BUILDER_PACK_DIR="$PWD/agent-specs/explainer" BUILDER_INPUT="Explain how a coffee machine
  works" mvn -Ppoc -pl poc/poc-1-no-code-runtime spring-boot:run`. Inputs are OS env vars
  (`BUILDER_*`), not `-D`.
- **The full loop:** `./run-loop.sh "<description>" "<input>"` builds a spec with the builder,
  captures the written pack, and runs it in the no-code runtime in one command.
- **In the browser:** start the studio and open its **Process** workspace to go from business
  documents to a published spec, or the **Compose** pane to wire one by hand
  ([[studio-process-workspace]], [[studio-compose-editor]]).
- **Required to run (not to build):** a model endpoint via env vars, **detected** from whichever is
  set — `OPENAI_CUSTOM_BASE_URL` + `OPENAI_CUSTOM_API_KEY` + `OPENAI_CUSTOM_MODELS` (any
  OpenAI-compatible server), or `OPENAI_API_KEY`, or `ANTHROPIC_API_KEY`, plus
  `EMBABEL_MODELS_DEFAULT_LLM` naming the model. Without any of them, endpoint-gated POCs fail with a
  **clear configuration error**, not an opaque model error — see [[model-endpoint]].
- **First files to read:** `README.md`, `docs/agent-spec-format.md`,
  `agent-specs/explainer/` (the sample pack), then
  `poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SpecRunner.java`.
- **One safe first change:** edit a prompt in `agent-specs/explainer/steps/writeExplainer.yml` and
  re-run POC 1 — you only change data, never code.
- **One tempting dangerous change:** deleting the validate-before-write guard in
  `poc/poc-3-builder-agent/.../BuilderAgent.java` (`writeAgentSpec`). That is the single invariant
  that keeps "generate with no code" safe — see [[validate-before-write]].

Full orientation: [[getting-started]].

## 3. Why does it exist?

It is a research/POC vehicle for the **"specs, not agents"** paradigm on Embabel. The thesis (see
`docs/03-embabel-spec-agent-builder-design.md`): Embabel *already* is a specs runtime — a
**spec** is just an **exported goal** (`export: true`), and the platform's non-LLM **GOAP**
planner *is* the spec router. The POCs prove the load-bearing facts (YAML ingestion
without compilation, NL→draft compilation, deterministic builder structure, spec composition, CLI
invocation, retrieval, eval-guided refinement, trust tiers, progressive disclosure, semantic
retrieval at scale, remote/MCP invocation, the spec Composer, the visual builder UI, the
remote-action bridge, GLiNER2 spec classification, spec lifecycle / drift detection, small-LLM
spec selection, and the real semantic `EmbeddingService`).
All three Phase 1 specs-layer components doc 03 lists (registry, composer, runtime adapter) are
built, and every hypothesis gap doc 03 §2 lists — including a real semantic `EmbeddingService`
(POC 20) — is now closed.
**POCs 24–27 probe what comes next**, outside the original claim set: which generation mechanism
produces a usable multi-agent flow ([[generation-approaches]]), whether the portable unit can be an
Anthropic `SKILL.md` instead of a YAML spec ([[skill-generation]]), whether a skill's own JavaScript can
execute inside the agent ([[skill-scripts-graaljs]]), and whether the builder can improve the process it
builds with — the verdict on that last one is *not proven* ([[what-the-pocs-prove]]). Two of those
landed in the product: POC 24's measurement is the builder's default technique *and* the reason the build now
weighs two candidate shapes ([[two-candidate-build]]), and POC 25's became the `--target skill`
output ([[skill-output-target]]).
See [[exported-goal-as-spec]] and [[what-the-pocs-prove]].

## 4. What happens when I run it?

Three entry points matter:

- **Run a spec (POC 1):** the fixed runtime loads an agent spec, validates it, ingests the YAML
  with zero compilation, plans a path from `UserInput` to the goal's output type, executes it
  (only the action bodies call the model), and returns the goal output. See [[runtime-run]].
- **Build a spec (POC 3):** the builder agent plans `compileFromDescription → validateDraft →
  writeAgentSpec`. Only the first step calls the model; validate and write are pure Java, and an
  invalid draft is never written. See [[builder-pipeline]].
- **Compose specs (POC 6):** two independent packs are loaded into one platform and the GOAP
  planner chains an action from each through their shared intermediate type. See
  [[spec-composition]].
- **Author a composite spec (POC 14):** specs are chained on a type-checked canvas and exported
  as a single composite agent spec, which then runs through the no-code runtime. See
  [[spec-composer]].
- **Build a spec in the browser (POC 15):** the visual builder UI — a drag-and-drop canvas that
  loads the spec palette, snaps only type-compatible connections, live-validates the chain, and
  exports the composite over REST — plus a curation-aware **retrieval pane** (POC 18 lifecycle), an
  **author pane** (POC 2), an **eval/refinement pane** (POC 9), and a **curate re-eval** (POC 18).
  See [[spec-composer]].
- **Author a process in the product (studio):** the home **Process** workspace runs
  Discover → Generate → Simulate → Publish over your business documents, and the **Compose** pane
  wires a GOAP flow by hand on a type-checked canvas and publishes it. See
  [[studio-process-workspace]] and [[studio-compose-editor]].

## 5. Where is data saved?

- **Agent specs** live under `agent-specs/<name>/` (samples: `explainer`, `compose-extract`,
  `compose-explain`, `shell-runner`, `reference-heavy`).
- **Built packs** are written by the builder to `./built-specs/<name>/` (configurable via
  `builder.output-dir`).
- **Blueprints** to author from live in `examples/blueprints/` — five corpora: Aurora, Sentinel,
  TradingAgents, agentic-software-factory, deer-flow. `smith blueprint` (or
  `examples/generate-blueprint.sh`) reconstructs one from a codebase.
- **Intermediate types** are pure data in the pack's `types/` directory — no compiled classes.
- The POCs, the `smith run` runtime, and the `smith build` builder have **no database** —
  persistence is plain files on disk (the runtime assembles a temp directory per run and leaves the
  source pack untouched). The **studio** optionally adds a **Postgres** database for saved packs,
  the discovery history, and run traces — hot-swappable from the UI, and the app still boots with
  none ([[smith-studio-storage]]). Its dev database is `docker/docker-compose-infra.yml` on port
  5433 ([[build-tooling]]).

## 6. What are the important moving parts?

- `smith-common` — the **format owner**: data model, YAML I/O, and validation. Framework-free.
  See [[agent-spec-model]] and [[validation-codes]].
- `poc/poc-1-no-code-runtime` (POC 1, **keystone**) — proves the no-compilation ingest + run path.
  See [[runtime-run]].
- `poc/poc-3-builder-agent` (POC 3) — the builder as a code-backed `@Agent`. See
  [[builder-pipeline]].
- `poc/poc-2-nl-compile` (POC 2), `poc/poc-4-agent-spec-format` (POC 4), `poc/poc-5-eval` (POC 5) — see
  [[architecture]].
- `poc/poc-6`…`poc-23` — the GOAP-based extensions: composition, runtime adapter, retrieval,
  eval-guided refinement, trust tiers, progressive disclosure, semantic retrieval at scale,
  remote/MCP invocation, the spec Composer, the visual builder UI, the remote-action bridge,
  GLiNER2 spec classification, spec lifecycle / drift detection, small-LLM spec selection, the
  real semantic `EmbeddingService`, install-time security scanning, the unified builder UI (POC 22),
  and remote embedding selection at scale (POC 23). See [[what-the-pocs-prove]].
- `poc/poc-24`…`poc-27` — the generation, portable-unit and self-improvement wave: single-shot vs the
  Embabel NL compiler ([[generation-approaches]]), blueprint → Agent Skills ([[skill-generation]]),
  in-JVM GraalJS skill scripts ([[skill-scripts-graaljs]]), and whether the builder can improve the
  process it builds with — verdict *not proven* ([[what-the-pocs-prove]]).
- `prod/` — the **product phase** ([[product-phase]]): `specsource` (folder/git sources +
  content hash), the `smith run` runtime (multi-pack ingest + trust tiers + the retired-pack gate +
  JSONL trace + the remote-action bridge the studio's Remote pane drives), the
  `smith build` builder (one description → **two candidates**, a single-agent pack and a
  multi-agent process, both scored and one recommended, behind an `AgentSpecGenerator` technique
  seam and a `--flow` seam that can hand the generation call to the builder's own pack, or a
  `SKILL.md` with `--target skill`; [[smith-builder]], [[two-candidate-build]],
  [[skill-output-target]]), and the `studio` (the driver of the self-improvement loop that rewrites
  failing steps from traces and republishes on proven improvement — the engine itself,
  `builder/improve/`, has lived in the builder since 2026-11-21 — exposed as a web product: REST
  surface + Vue UI + loop scheduling + the process workspace). All model calls in `prod/` go through Embabel's `Ai`,
  not the endpoint directly. `runtime-a2a` is the one prod module that gains a servlet engine
  from a dependency rather than declaring one — an unauthenticated local development endpoint
  for another process to ask for a run ([[a2a-endpoint]]). The studio also listens, on its own
  starter, because it is the UI. `skillevaluator` is the Tier 1 **static skill validator** — a
  zero-model-call port of a Python `tier1` validator — and its only consumer is the `inspect`
  gate of the builder's skill target ([[skill-output-target]]). See
  [[spec-sources]], [[smith-runtime]], [[smith-builder]], [[smith-studio]], [[smith-studio-web]],
  [[studio-process-workspace]], [[studio-compose-editor]] and [[model-calls-go-through-ai]].
- Root tooling — the `Makefile` (stable Maven entry points), `setup.sh` (developer setup), the
  pre-commit hooks, and the SpringChecks/style gate that runs at Maven's `validate` phase.
  See [[build-tooling]].

## 7. What should I avoid breaking?

- **Validate-before-write** (design D5): the builder must never write a draft that fails
  validation. See [[validate-before-write]].
- **The format contract**: builder (producer) and runtime (consumer) both depend on
  `smith-common`; changing field names or the layout breaks both sides. See [[agent-spec-format]].
- **Endpoint gating**: keep the model endpoint env-driven and keep the "clear config error when
  unresolvable" behaviour. See [[model-endpoint]].
- **The style gate**: SpringChecks + the mandatory SPDX license / author header run at Maven's
  `validate` phase for every module's main sources, so a violation fails the build before
  compilation. See [[build-tooling]].
- **No direct endpoint calls in `prod/`**: every production model call goes through Embabel's `Ai`,
  and two source-level tests fail the build on a `java.net.http` import under `src/main/java`. Adding
  a raw caller reintroduces a second, silent model-name decision. See [[model-calls-go-through-ai]].
- **The studio's forced servlet mode**: `main()` sets `web-application-type=SERVLET` as a system
  property for a non-obvious reason (a transitive jar ships a `none` default). Do not "clean it up".
  See [[smith-studio-storage]].
- **The recommendation rule**: the multi-agent candidate wins only when it is valid *and* strictly
  out-scores the single-agent pack. Weakening `>` to `>=`, or letting an invalid process be
  recommended, silently ships the riskier shape. See [[two-candidate-build]].
- **The `Publishable` seam**: `Publisher.publish(Publishable, overwrite, push)` is the one abstract
  method every artifact publishes through. Changing its signature breaks *dependent modules' test
  lambdas*, not their main code — so it stays invisible until someone test-compiles another module.
  Run `mvn -pl <module> -am test-compile` across `prod/` after touching it.
- **The reply envelope named in `builder-spec-one-shot`'s step prompt**: the drafting step spells the
  one-key object (`{"json": …}`) its goal type declares. No validator reads prose about output shape, so
  dropping that sentence surfaces only as the pack flow's "did not return valid agent spec JSON" after a
  paid build. `BuilderAgentSpecPackTest` pins it by reading the key from the type, not from a Java
  literal. See [[smith-builder]].

## 8. Where do I look first?

| Question | Start here |
| --- | --- |
| What did the POCs actually prove? | [[what-the-pocs-prove]] · `docs/04-poc-run-results.md` |
| How is an agent spec laid out? | [[agent-spec-format]] · `docs/agent-spec-format.md` |
| What are the data classes? | [[agent-spec-model]] · `smith-common/.../model/` |
| How does a spec run? | [[runtime-run]] · `SpecRunner.java` |
| How is a spec built? | [[builder-pipeline]] · `BuilderAgent.java` |
| How are specs composed? | [[spec-composition]] · `CompositionRunner.java` |
| How is a composite spec authored + exported? | [[spec-composer]] · `poc/poc-14-composer/` |
| How do I build a spec in the browser (builder UI)? | [[spec-composer]] · `poc/poc-15-composer-ui/` |
| How does a spec call a CLI? | [[runtime-adapter]] · `shell-runner/` pack |
| How does a spec invoke a remote action over HTTP? | [[smith-runtime]] · [[runtime-adapter]] · `poc/poc-16-remote-action/` · prod: `runtime/remote/` |
| How is a spec scored / refined? | [[eval-scoring]] · [[eval-guided-refinement]] |
| How are specs retrieved at scale? | [[retrieval-shadowing]] · [[shadowing-remedies]] · prod: [[smith-studio-web]] |
| How are trust tiers enforced? | [[trust-tiers]] · [[spec-security-scanning]] |
| How is a spec vetted before install? | [[spec-security-scanning]] |
| How does a spec's lifecycle / drift work, and what does it block? | [[spec-lifecycle]] · [[smith-runtime]] |
| How does progressive disclosure bound context? | [[progressive-disclosure]] |
| What is an "exported goal"? | [[exported-goal-as-spec]] |
| How does zero-compilation work? | [[no-code-ingestion]] |
| How do the POCs become the product? | [[product-phase]] · `prod/` |
| How are spec sources (folder/git) resolved? | [[spec-sources]] |
| How does the `smith run` runtime work (CLI + trace)? | [[smith-runtime]] |
| How does another process ask this product to run a goal (A2A)? | [[a2a-endpoint]] · `prod/embabel-smith-runtime-a2a/` |
| How does the `smith build` builder work (pipeline + publish)? | [[smith-builder]] |
| How does the build choose between a single agent and a multi-agent process? | [[two-candidate-build]] · `builder/core/CandidateRecommender.java` |
| How do I build a `SKILL.md` instead of a pack? | [[skill-output-target]] · `builder/skill/` |
| How is a drafted skill vetted before the eval is paid for? | [[skill-output-target]] · `builder/skill/SkillInspector.java` |
| How does a spec self-improve from its traces? | [[smith-studio]] |
| How is the studio exposed as a web product (REST + UI + loop control)? | [[smith-studio-web]] |
| How does the studio store packs/traces in Postgres (hot-swap + pluggable traces)? | [[smith-studio-storage]] |
| How do I author a process from business documents in the studio? | [[studio-process-workspace]] · `examples/blueprints/` |
| How do I wire a GOAP flow by hand in the studio? | [[studio-compose-editor]] · `frontend/src/compose/model.ts` |
| Which generation technique should I use, and why? | [[generation-approaches]] (POC 24) · `poc/poc-24-generation-approaches/results/comparison.md` |
| Which model does production actually call, and who decides? | [[model-calls-go-through-ai]] · `builder/core/AiSpecPromptCaller.java` |
| How does the builder pick a generation technique? | [[smith-builder]] · `builder/core/GenerationTechnique.java` |
| Can a pack's own agent call the format validators? | [[smith-builder]] · `builder/tools/` |
| Who issues the generation call — the Java stages or the builder's own pack? | [[smith-builder]] · `builder/cli/BuildFlow.java` |
| What does CI actually verify, and what does a green run not cover? | [[build-tooling]] · `.github/workflows/offline-verification.yml` |
| Can the portable unit be a `SKILL.md` instead of a YAML spec? | [[skill-generation]] (POC 25) |
| How does a skill's JavaScript run inside the agent? | [[skill-scripts-graaljs]] (POC 26) |
| What build commands should I use? | [[build-tooling]] · `Makefile` |

## Page catalog

**Overview** — [[what-is-embabel-smith]] · [[getting-started]] · [[architecture]] ·
[[what-the-pocs-prove]] · [[product-phase]] · [[build-tooling]]
**Entities** — [[agent-spec-format]] · [[agent-spec-model]]
**Concepts** — [[exported-goal-as-spec]] · [[no-code-ingestion]] · [[model-endpoint]]
**Flows** — [[runtime-run]] · [[builder-pipeline]] · [[eval-scoring]] · [[spec-composition]] ·
[[eval-guided-refinement]]
**Features** — [[nl-compile]] · [[spec-format-on-demand]] · [[runtime-adapter]] ·
[[retrieval-shadowing]] · [[shadowing-remedies]] · [[spec-lifecycle]] · [[trust-tiers]] ·
[[spec-security-scanning]] · [[progressive-disclosure]] · [[spec-composer]] · [[spec-sources]] ·
[[smith-runtime]] · [[a2a-endpoint]] · [[smith-builder]] · [[smith-studio]] · [[smith-studio-web]] ·
[[smith-studio-storage]] · [[studio-process-workspace]] · [[studio-compose-editor]] ·
[[generation-approaches]] · [[skill-generation]] · [[skill-scripts-graaljs]] ·
[[two-candidate-build]] · [[skill-output-target]]
**Risks** — [[validate-before-write]] · [[model-calls-go-through-ai]]
**Reference** — [[validation-codes]] · [[sample-explainer]]
