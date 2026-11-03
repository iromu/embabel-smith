---
title: "What is Embabel Smith?"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "README.md"
  - "docs/03-embabel-skills-agent-builder-design.md"
  - "docs/04-poc-run-results.md"
  - "pom.xml"
updated_at: "2026-11-03"
---

# What is Embabel Smith?

Embabel Smith is a **greenfield, multi-module Maven proof-of-concept** on **Embabel 1.5.3** and
**Java 21**. It proves one idea end to end:

> A *builder agent* turns a natural-language description into a **skill-pack** (a directory of
> pure YAML, no code), validates it before writing, and emits it. A separate **fixed no-code
> runtime** ingests that skill-pack with **zero compilation** and runs a plan over its exported
> goal.

Two roles, one contract:

- **Producer** — `poc/poc-3-builder-agent` (POC 3) writes the skill-pack.
- **Consumer** — `poc/poc-1-no-code-runtime` (POC 1) reads it and runs it.
- **Shared contract** — `smith-common` owns the skill-pack format so both sides are checked
  against the same schema. See [[skill-pack-format]] and [[skill-pack-model]].

## The core idea: "skills, not agents"

The POC is a vehicle for a research thesis (see
`docs/03-embabel-skills-agent-builder-design.md`). The claim: Embabel *already* implements the
"skills, not agents" paradigm from the agent-skills literature — it just calls skills **exported
goals** (`export: true`) and the skill router the platform's **GOAP** planner. See
[[exported-goal-as-skill]].

The full "skills layer" — a semantic **Skill Registry**, a drag-and-drop **Skill Composer**, and
**MCP/remote** runtime adapters — is the long-term target (doc 03 §6). The POCs have now built and
exercised most of it; see [[what-the-pocs-prove]] for what is proven and what is still open.

## What each POC proves

Each POC is an independent module so a failure isolates to one claim (design D1). See
[[architecture]] for the module map and run commands:

**Feasibility core (POC 1–5):**

- **POC 1** — a skill-pack can be ingested with no compilation and run to a goal output.
- **POC 2** — a description can be compiled into a draft (actions + goal + types + issues).
- **POC 3** — a builder agent produces a *validated* skill-pack with deterministic structure.
- **POC 4** — an exported goal is a callable skill whose chain loads on demand.
- **POC 5** — a goal-type skill can be scored, and a failing task can drive a prompt rewrite.

**Skills-first extensions (POC 6–20):**

- **POC 6** — skills compose: GOAP chains an action from two independent packs through a shared
  intermediate type. See [[skill-composition]].
- **POC 7** — a skill invokes a real CLI via an injected `shell` tool group. See
  [[runtime-adapter]].
- **POC 8** — a lexical retriever disambiguates among same-typed skills (skill shadowing). See
  [[retrieval-shadowing]].
- **POC 9** — eval-guided refinement lifts a weak LLM-authored skill (0% → 100%). See
  [[eval-guided-refinement]].
- **POC 10** — trust tiers gate tool groups; a global guardrail blocks a prompt injection. See
  [[trust-tiers]].
- **POC 11** — the plan context stays constant while a bundled reference grows 100×. See
  [[progressive-disclosure]].
- **POC 12** — semantic retrieval at scale: clean at 200+ distinct skills, but near-duplicate
  shadowing collapses P@1 for both the lexical and a dense retriever — a curation problem.
- **POC 13** — a skill is auto-published as a remote MCP tool and invoked over the network. See
  [[runtime-adapter]].
- **POC 14** — the skill Composer: skills chain on a type-checked canvas and export as a composite
  skill-pack that runs end-to-end. See [[skill-composer]].
- **POC 15** — the visual builder UI: a drag-and-drop canvas over POC 14's model, plus a
  curation-aware **retrieval pane** (POC 18), an **author pane** (POC 2), an **eval/refinement
  pane** (POC 9), and a **curate re-eval** (POC 18). See [[skill-composer]].
- **POC 16** — a GOAP plan plans over, and executes over HTTP, a remote action running on a
  sidecar. See [[runtime-adapter]].
- **POC 17** — a GLiNER2 transformer scores the query by *meaning* and breaks the shadowing tie
  lexical and dense text-similarity cannot.
- **POC 18** — skill lifecycle / drift: a proactive re-eval moves a skill through
  incubating → active → degraded → retired (applied in the POC 15 canvas).
- **POC 19** — a small (0.6B) LLM, served in a Docker container, selects skills — beats text
  similarity, trails the transformer.
- **POC 20** — a real semantic `EmbeddingService` (an ONNX sentence-encoder) behind the
  `SkillScorer` seam.

The per-POC verdicts, caveats, and run evidence are distilled in [[what-the-pocs-prove]].

## Offline vs. endpoint-gated

The whole project **builds and tests offline** (`mvn -q verify`). Only the steps that actually call
a model are **endpoint-gated** and require a live OpenAI-compatible endpoint — see
[[model-endpoint]]. POC 8 (retrieval) and POC 11 (progressive disclosure) are fully offline; the
rest of the run paths are endpoint-gated. This split is what lets the repo verify all non-LLM logic
in CI without any model.
