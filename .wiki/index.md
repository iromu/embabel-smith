---
title: "Embabel Smith Wiki"
type: "index"
status: "active"
language: "default"
last_commit: "8f4c1a2fd6446c223344a6d76f5e1aa18bd6a3c8"
updated_at: "2026-11-09"
---

# Embabel Smith

A **skills-first agent builder** on the Embabel platform (v1.5.4, Java 21). One part *builds* a
skill; the other part *runs* one. The POCs under `poc/` prove the idea end to end; the product
modules under `prod/` ([[product-phase]]) turn that proven logic into a working builder and runtime.
This wiki explains how the pieces fit so a newcomer can orient fast and verify claims in code.

## 1. What is this?

Embabel Smith is a greenfield, multi-module Maven proof-of-concept (POC) that proves one idea end
to end: **a builder agent can turn a natural-language description into a deployable, no-code
"skill-pack", and a fixed runtime can ingest that skill-pack and run it — with zero compilation.**

A **skill-pack** is a small directory of pure YAML (no code, no build step). It is the unit of
capability that the builder emits and the runtime ingests. See [[skill-pack-format]].

## 2. Get started

- **Build & test everything (offline):** `mvn -q verify`. Every module's tests pass with no model
  endpoint. The model-touching tests are *endpoint-gated* and skipped offline.
- **Run a POC:** `mvn install -DskipTests` once (so sibling modules resolve), then run one module,
  e.g. `BUILDER_PACK_DIR="$PWD/skill-packs/explainer" BUILDER_INPUT="Explain how a coffee machine
  works" mvn -pl poc/poc-1-no-code-runtime spring-boot:run`. Inputs are OS env vars (`BUILDER_*`),
  not `-D`.
- **The full loop:** `./run-loop.sh "<description>" "<input>"` builds a skill with the builder,
  captures the written pack, and runs it in the no-code runtime in one command.
- **Required to run (not to build):** an OpenAI-compatible model endpoint via env vars
  (`OPENAI_CUSTOM_BASE_URL`, `OPENAI_CUSTOM_API_KEY`, `OPENAI_CUSTOM_MODELS`,
  `EMBABEL_MODELS_DEFAULT_LLM`). Without them, endpoint-gated POCs fail with a **clear
  configuration error**, not an opaque model error — see [[model-endpoint]].
- **First files to read:** `README.md`, `docs/skill-pack-format.md`,
  `skill-packs/explainer/` (the sample pack), then
  `poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SkillRunner.java`.
- **One safe first change:** edit a prompt in `skill-packs/explainer/steps/writeExplainer.yml` and
  re-run POC 1 — you only change data, never code.
- **One tempting dangerous change:** deleting the validate-before-write guard in
  `poc/poc-3-builder-agent/.../BuilderAgent.java` (`writeSkillPack`). That is the single invariant
  that keeps "generate with no code" safe — see [[validate-before-write]].

Full orientation: [[getting-started]].

## 3. Why does it exist?

It is a research/POC vehicle for the **"skills, not agents"** paradigm on Embabel. The thesis (see
`docs/03-embabel-skills-agent-builder-design.md`): Embabel *already* is a skills runtime — a
**skill** is just an **exported goal** (`export: true`), and the platform's non-LLM **GOAP**
planner *is* the skill router. The twenty POCs prove the load-bearing facts (YAML ingestion
without compilation, NL→draft compilation, deterministic builder structure, skill composition, CLI
invocation, retrieval, eval-guided refinement, trust tiers, progressive disclosure, semantic
retrieval at scale, remote/MCP invocation, the skill Composer, the visual builder UI, the
remote-action bridge, GLiNER2 skill classification, skill lifecycle / drift detection, small-LLM
skill selection, and the real semantic `EmbeddingService`).
All three Phase 1 skills-layer components doc 03 lists (registry, composer, runtime adapter) are
built, and every hypothesis gap doc 03 §2 lists — including a real semantic `EmbeddingService`
(POC 20) — is now closed.
See [[exported-goal-as-skill]] and [[what-the-pocs-prove]].

## 4. What happens when I run it?

Three entry points matter:

- **Run a skill (POC 1):** the fixed runtime loads a skill-pack, validates it, ingests the YAML
  with zero compilation, plans a path from `UserInput` to the goal's output type, executes it
  (only the action bodies call the model), and returns the goal output. See [[runtime-run]].
- **Build a skill (POC 3):** the builder agent plans `compileFromDescription → validateDraft →
  writeSkillPack`. Only the first step calls the model; validate and write are pure Java, and an
  invalid draft is never written. See [[builder-pipeline]].
- **Compose skills (POC 6):** two independent packs are loaded into one platform and the GOAP
  planner chains an action from each through their shared intermediate type. See
  [[skill-composition]].
- **Author a composite skill (POC 14):** skills are chained on a type-checked canvas and exported
  as a single composite skill-pack, which then runs through the no-code runtime. See
  [[skill-composer]].
- **Build a skill in the browser (POC 15):** the visual builder UI — a drag-and-drop canvas that
  loads the skill palette, snaps only type-compatible connections, live-validates the chain, and
  exports the composite over REST — plus a curation-aware **retrieval pane** (POC 18 lifecycle), an
  **author pane** (POC 2), an **eval/refinement pane** (POC 9), and a **curate re-eval** (POC 18).
  See [[skill-composer]].

## 5. Where is data saved?

- **Skill-packs** live under `skill-packs/<name>/` (samples: `explainer`, `compose-extract`,
  `compose-explain`, `shell-runner`, `reference-heavy`).
- **Built packs** are written by the builder to `./built-skills/<name>/` (configurable via
  `builder.output-dir`).
- **Intermediate types** are pure data in the pack's `types/` directory — no compiled classes.
- There is **no database**; persistence is plain files on disk. The runtime assembles a temp
  directory per run and leaves the source pack untouched.

## 6. What are the important moving parts?

- `smith-common` — the **format owner**: data model, YAML I/O, and validation. Framework-free.
  See [[skill-pack-model]] and [[validation-codes]].
- `poc/poc-1-no-code-runtime` (POC 1, **keystone**) — proves the no-compilation ingest + run path.
  See [[runtime-run]].
- `poc/poc-3-builder-agent` (POC 3) — the builder as a code-backed `@Agent`. See
  [[builder-pipeline]].
- `poc/poc-2-nl-compile` (POC 2), `poc/poc-4-skill-format` (POC 4), `poc/poc-5-eval` (POC 5) — see
  [[architecture]].
- `poc/poc-6`…`poc-23` — the skills-first extensions: composition, runtime adapter, retrieval,
  eval-guided refinement, trust tiers, progressive disclosure, semantic retrieval at scale,
  remote/MCP invocation, the skill Composer, the visual builder UI, the remote-action bridge,
  GLiNER2 skill classification, skill lifecycle / drift detection, small-LLM skill selection, the
  real semantic `EmbeddingService`, install-time security scanning, the unified builder UI (POC 22),
  and remote embedding selection at scale (POC 23). See [[what-the-pocs-prove]].
- `prod/` — the **product phase** ([[product-phase]]): `skillsource` (folder/git sources +
  content hash), the `smith run` runtime (multi-pack ingest + trust tiers + JSONL trace), the
  `smith build` builder (NL → validated pack → publish), and the `studio` (the self-improvement
  loop that rewrites failing steps from traces and republishes on proven improvement, exposed as a
  web product: REST surface + Vue UI + loop scheduling). See
  [[skill-sources]], [[smith-runtime]], [[smith-builder]], [[smith-studio]], and
  [[smith-studio-web]].

## 7. What should I avoid breaking?

- **Validate-before-write** (design D5): the builder must never write a draft that fails
  validation. See [[validate-before-write]].
- **The format contract**: builder (producer) and runtime (consumer) both depend on
  `smith-common`; changing field names or the layout breaks both sides. See [[skill-pack-format]].
- **Endpoint gating**: keep the model endpoint env-driven and keep the "clear config error when
  unresolvable" behaviour. See [[model-endpoint]].

## 8. Where do I look first?

| Question | Start here |
| --- | --- |
| What did the POCs actually prove? | [[what-the-pocs-prove]] · `docs/04-poc-run-results.md` |
| How is a skill-pack laid out? | [[skill-pack-format]] · `docs/skill-pack-format.md` |
| What are the data classes? | [[skill-pack-model]] · `smith-common/.../model/` |
| How does a skill run? | [[runtime-run]] · `SkillRunner.java` |
| How is a skill built? | [[builder-pipeline]] · `BuilderAgent.java` |
| How are skills composed? | [[skill-composition]] · `CompositionRunner.java` |
| How is a composite skill authored + exported? | [[skill-composer]] · `poc/poc-14-composer/` |
| How do I build a skill in the browser (builder UI)? | [[skill-composer]] · `poc/poc-15-composer-ui/` |
| How does a skill call a CLI? | [[runtime-adapter]] · `shell-runner/` pack |
| How does a skill invoke a remote action over HTTP? | [[runtime-adapter]] · `poc/poc-16-remote-action/` |
| How is a skill scored / refined? | [[eval-scoring]] · [[eval-guided-refinement]] |
| How are skills retrieved at scale? | [[retrieval-shadowing]] · [[shadowing-remedies]] |
| How are trust tiers enforced? | [[trust-tiers]] · [[skill-security-scanning]] |
| How is a skill vetted before install? | [[skill-security-scanning]] |
| How does a skill's lifecycle / drift work? | [[skill-lifecycle]] |
| How does progressive disclosure bound context? | [[progressive-disclosure]] |
| What is an "exported goal"? | [[exported-goal-as-skill]] |
| How does zero-compilation work? | [[no-code-ingestion]] |
| How do the POCs become the product? | [[product-phase]] · `prod/` |
| How are skill sources (folder/git) resolved? | [[skill-sources]] |
| How does the `smith run` runtime work (CLI + trace)? | [[smith-runtime]] |
| How does the `smith build` builder work (pipeline + publish)? | [[smith-builder]] |
| How does a skill self-improve from its traces? | [[smith-studio]] |
| How is the studio exposed as a web product (REST + UI + loop control)? | [[smith-studio-web]] |

## Page catalog

**Overview** — [[what-is-embabel-smith]] · [[getting-started]] · [[architecture]] ·
[[what-the-pocs-prove]] · [[product-phase]]
**Entities** — [[skill-pack-format]] · [[skill-pack-model]]
**Concepts** — [[exported-goal-as-skill]] · [[no-code-ingestion]] · [[model-endpoint]]
**Flows** — [[runtime-run]] · [[builder-pipeline]] · [[eval-scoring]] · [[skill-composition]] ·
[[eval-guided-refinement]]
**Features** — [[nl-compile]] · [[skill-format-on-demand]] · [[runtime-adapter]] ·
[[retrieval-shadowing]] · [[shadowing-remedies]] · [[skill-lifecycle]] · [[trust-tiers]] ·
[[skill-security-scanning]] · [[progressive-disclosure]] · [[skill-composer]] · [[skill-sources]] ·
[[smith-runtime]] · [[smith-builder]] · [[smith-studio]] · [[smith-studio-web]]
**Risks** — [[validate-before-write]]
**Reference** — [[validation-codes]] · [[sample-explainer]]
