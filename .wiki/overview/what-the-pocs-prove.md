---
title: "What the POCs prove (vs. the theory)"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "docs/04-poc-run-results.md"
  - "docs/01-embabel-agent-builder-research.md"
  - "docs/02-agent-skills-research.md"
  - "docs/03-embabel-skills-agent-builder-design.md"
updated_at: "2026-11-02"
---

# What the POCs prove (vs. the theory)

This page is the **conclusion** of the POC run (2026-11-02) against the hypothesis in docs 01–03.
It answers: what did the fifteen POCs actually prove, what did they not, and what is still missing.
The raw run evidence (green loop, offline baseline, defects fixed) lives in
`docs/04-poc-run-results.md` §5; this page distills the verdict.

## The hypothesis, in one line

> A single Embabel agent can load a YAML skill (an exported goal) on demand, run it via a
> deterministic (non-LLM) GOAP plan, and produce the skill from a natural-language description —
> no compilation, no multi-agent fleet.

Doc 01: *"Embabel already ships ~70% of the pieces; the missing piece is the builder UI."* Doc 03
adds that making it **skills-first at scale** needs three more components: a Skill **Registry**, a
**Composer**, and a **Runtime Adapter**.

## Verdict

The POCs **prove the feasibility core** (the "70% already there") and confirm doc 03's claim that
*Embabel is a skills runtime*, now extended well beyond the single-skill case:

- **Proven:** no-compile ingestion · exported-goal-as-skill · portable lossless pack · NL→draft ·
  the end-to-end loop · **skill composition** (POC 6) · **CLI invocation** (POC 7) ·
  **lexical retrieval/disambiguation** (POC 8) · **eval-guided refinement** (POC 9) ·
  **trust tiers + guardrails** (POC 10) · **progressive disclosure under load** (POC 11) ·
  **retrieval at 200+ skill scale + the shadowing failure mode** (POC 12) · **remote / MCP
  invocation** (POC 13) · **the skill Composer** (POC 14) · **the visual builder UI** (POC 15) ·
  eval wiring + **score stability** (POC 5) · any OpenAI-compatible endpoint · single-agent,
  no-orchestration execution. All three Phase 1 components doc 03 lists (Registry, Composer,
  Runtime Adapter) are now built — and the builder UI doc 01 names as the missing piece now exists.
- **Not proven / not built:** a real semantic **`EmbeddingService`** (POC 12 uses an offline
  feature-hashing stand-in). The drag-and-drop UI is now built (POC 15); it is a chaining editor
  only — no NL-authoring or eval pane wired into the same canvas yet.
- **Confirmed negative, now measured, with a proven fix:** LLM-authored skill **quality** is weak
  at the source (doc 02 caveat #2) — the `DraftQuality` classifier now reports a draft's degenerate
  goal shape (POC 2 raw, POC 3 before/after the builder's normalization, e.g. `NO_OP_GOAL →
  HEALTHY`) — **and** eval-guided refinement demonstrably repairs the action quality (POC 9: 0% →
  100%).

## What each extension POC adds

| POC | Proves | Caveat |
| --- | --- | --- |
| [[skill-composition]] (6) | GOAP chains `extractTopic → writeExplainer` across two packs through a shared `TopicBrief` | two-skill chain; no orchestration code |
| [[runtime-adapter]] (7) | a `shell` tool group ran a real command (`uname -s` → `Linux`) | `ToolGroupMetadata` needed a reflection workaround; the `remote`/MCP half is POC 13 |
| [[retrieval-shadowing]] (8) | lexical (TF-IDF/cosine) retriever, P@1 100% over 14 same-typed skills | small scale; the 200+ scale is covered by POC 12 |
| [[eval-guided-refinement]] (9) | the SkillAxe loop lifts a weak skill 0% → 100% | a repair loop, not a compiler fix; the raw draft is still weak |
| [[trust-tiers]] (10) | per-tier allowlist refused `shell` under T1; a guardrail blocked an injection | the guardrail is a normalized multi-pattern matcher, not a semantic detector |
| [[progressive-disclosure]] (11) | plan context constant while a reference grows 3 KB → 313 KB (×100) | a measurement POC, not a runtime feature |
| POC 12 (semantic retrieval at scale) | P@1 100% at 191 distinct skills; near-duplicate shadowing collapses P@1 for **both** lexical and dense retrievers (281 skills) | a measurement POC; the fix is library curation / a higher-level router, not just a better embedding |
| [[runtime-adapter]] (13) | an exported goal is auto-published as an MCP tool over Streamable HTTP and invoked by a separate client over the network | the goal method needs both `@Action` + `@AchievesGoal`; the call must supply all `UserInput` fields (`content` + `timestamp`) |
| [[skill-composer]] (14) | skills chain on a type-checked canvas; the exported composite pack re-validates and runs end-to-end via GOAP | the headless graph model POC 15's UI sits on |
| [[skill-composer]] (15) | a browser drag-and-drop canvas loads the palette, snaps only type-compatible connections, live-validates the chain, and exports the composite over REST | the "builder UI" doc 01 names as the missing piece; a chaining editor only — no NL-authoring or eval pane yet |

## Still open

All Phase 1 POCs — and the builder UI — are built. Remaining follow-ups (non-Phase-1, hardening
rather than new POCs):

- **Wiring the other POCs into the POC 15 canvas** — an NL-authoring pane (POC 2/3) and an
  eval/refinement pane (POC 5/9) on the same surface.
- **A real semantic `EmbeddingService`** behind the `SkillScorer` seam (POC 8 lexical, POC 12
  offline dense stand-in).
