---
title: "Embabel Smith Wiki"
type: "index"
status: "active"
language: "default"
last_commit: "7805939705cee28e1bae131f740ad2bd2f463862"
updated_at: "2026-11-15"
---

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

- **Build & test everything (offline):** `mvn -q verify`. Every module's tests pass with no model
  endpoint. The model-touching tests are *endpoint-gated* and skipped offline.
- **Prefer the Makefile targets** (`make test`, `make test-module MODULE=…`, `make lint`): `make test`
  sources `~/.config/env/llm.env` first, so the endpoint-gated tests actually run instead of silently
  skipping ([[build-tooling]]).
- **Once per clone:** `make setup` installs the pre-commit hooks (gitleaks, whitespace, `make lint`).
  Without it, a fresh clone commits unchecked.
- **Run a POC:** `mvn install -DskipTests` once (so sibling modules resolve), then run one module,
  e.g. `BUILDER_PACK_DIR="$PWD/agent-specs/explainer" BUILDER_INPUT="Explain how a coffee machine
  works" mvn -pl poc/poc-1-no-code-runtime spring-boot:run`. Inputs are OS env vars (`BUILDER_*`),
  not `-D`.
- **The full loop:** `./run-loop.sh "<description>" "<input>"` builds a spec with the builder,
  captures the written pack, and runs it in the no-code runtime in one command.
- **In the browser:** start the studio and open its **Process** workspace to go from business
  documents to a published spec, or the **Compose** pane to wire one by hand
  ([[studio-process-workspace]], [[studio-compose-editor]]).
- **Required to run (not to build):** an OpenAI-compatible model endpoint via env vars
  (`OPENAI_CUSTOM_BASE_URL`, `OPENAI_CUSTOM_API_KEY`, `OPENAI_CUSTOM_MODELS`,
  `EMBABEL_MODELS_DEFAULT_LLM`). Without them, endpoint-gated POCs fail with a **clear
  configuration error**, not an opaque model error — see [[model-endpoint]].
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
**POCs 24–26 probe what comes next**, outside the original claim set: which generation mechanism
produces a usable multi-agent flow ([[generation-approaches]]), whether the portable unit can be an
Anthropic `SKILL.md` instead of a YAML spec ([[skill-generation]]), and whether a skill's own
JavaScript can execute inside the agent ([[skill-scripts-graaljs]]). Two of those landed in the
product: POC 24's measurement is the builder's default technique *and* the reason the build now
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
- **Blueprints** to author from live in `examples/blueprints/` (Aurora, Sentinel, TradingAgents).
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
- `poc/poc-24`…`poc-26` — the generation and portable-unit wave: single-shot vs the Embabel NL
  compiler ([[generation-approaches]]), blueprint → Agent Skills ([[skill-generation]]), and
  in-JVM GraalJS skill scripts ([[skill-scripts-graaljs]]).
- `prod/` — the **product phase** ([[product-phase]]): `specsource` (folder/git sources +
  content hash), the `smith run` runtime (multi-pack ingest + trust tiers + JSONL trace), the
  `smith build` builder (one description → **two candidates**, a single-agent pack and a
  multi-agent process, both scored and one recommended, behind an `AgentSpecGenerator` technique
  seam, or a `SKILL.md` with `--target skill`; [[smith-builder]], [[two-candidate-build]],
  [[skill-output-target]]), and the `studio` (the self-improvement loop that rewrites failing steps
  from traces and republishes on proven improvement, exposed as a web product: REST surface + Vue UI
  + loop scheduling + the process workspace). All model calls in `prod/` go through Embabel's `Ai`,
  not the endpoint directly. See
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
| How does a spec invoke a remote action over HTTP? | [[runtime-adapter]] · `poc/poc-16-remote-action/` |
| How is a spec scored / refined? | [[eval-scoring]] · [[eval-guided-refinement]] |
| How are specs retrieved at scale? | [[retrieval-shadowing]] · [[shadowing-remedies]] |
| How are trust tiers enforced? | [[trust-tiers]] · [[spec-security-scanning]] |
| How is a spec vetted before install? | [[spec-security-scanning]] |
| How does a spec's lifecycle / drift work? | [[spec-lifecycle]] |
| How does progressive disclosure bound context? | [[progressive-disclosure]] |
| What is an "exported goal"? | [[exported-goal-as-spec]] |
| How does zero-compilation work? | [[no-code-ingestion]] |
| How do the POCs become the product? | [[product-phase]] · `prod/` |
| How are spec sources (folder/git) resolved? | [[spec-sources]] |
| How does the `smith run` runtime work (CLI + trace)? | [[smith-runtime]] |
| How does the `smith build` builder work (pipeline + publish)? | [[smith-builder]] |
| How does the build choose between a single agent and a multi-agent process? | [[two-candidate-build]] · `builder/core/CandidateRecommender.java` |
| How do I build a `SKILL.md` instead of a pack? | [[skill-output-target]] · `builder/skill/` |
| How does a spec self-improve from its traces? | [[smith-studio]] |
| How is the studio exposed as a web product (REST + UI + loop control)? | [[smith-studio-web]] |
| How does the studio store packs/traces in Postgres (hot-swap + pluggable traces)? | [[smith-studio-storage]] |
| How do I author a process from business documents in the studio? | [[studio-process-workspace]] · `examples/blueprints/` |
| How do I wire a GOAP flow by hand in the studio? | [[studio-compose-editor]] · `frontend/src/compose/model.ts` |
| Which generation technique should I use, and why? | [[generation-approaches]] (POC 24) · `poc/poc-24-generation-approaches/results/comparison.md` |
| Which model does production actually call, and who decides? | [[model-calls-go-through-ai]] · `builder/core/AiSpecPromptCaller.java` |
| How does the builder pick a generation technique? | [[smith-builder]] · `builder/core/GenerationTechnique.java` |
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
[[smith-runtime]] · [[smith-builder]] · [[smith-studio]] · [[smith-studio-web]] ·
[[smith-studio-storage]] · [[studio-process-workspace]] · [[studio-compose-editor]] ·
[[generation-approaches]] · [[skill-generation]] · [[skill-scripts-graaljs]] ·
[[two-candidate-build]] · [[skill-output-target]]
**Risks** — [[validate-before-write]] · [[model-calls-go-through-ai]]
**Reference** — [[validation-codes]] · [[sample-explainer]]
