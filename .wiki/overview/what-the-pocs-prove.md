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
updated_at: "2026-11-03"
---

# What the POCs prove (vs. the theory)

This page is the **conclusion** of the POC run (2026-11-02, re-verified 2026-11-03) against the
hypothesis in docs 01–03. It answers: what did the twenty POCs actually prove, what did they not,
and what is still missing. The raw run evidence (green loop, offline baseline, defects fixed) lives
in `docs/04-poc-run-results.md` §5; this page distills the verdict.

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
  invocation** (POC 13) · **the skill Composer** (POC 14) · **the visual builder UI** (POC 15, now
  with retrieval / author / eval-refinement / curate panes) · **the remote-action bridge** (POC 16)
  · **a GLiNER2 transformer breaks the shadowing tie** (POC 17) · **skill lifecycle / drift
  detection** (POC 18) · **small-LLM skill selection + self-contained Docker model serving** (POC
  19) · **the real semantic `EmbeddingService`** (POC 20) · eval wiring + **score stability** (POC
  5) · any OpenAI-compatible endpoint · single-agent, no-orchestration execution. All three Phase 1
  components doc 03 lists (Registry, Composer, Runtime Adapter) are now built — and the builder UI
  doc 01 names as the missing piece now exists. **All three of doc 03 §2.3's runtime bridges are
  proven** (shell → POC 7, MCP → POC 13, remote action → POC 16).
- **Not proven / not built:** a real semantic **`EmbeddingService`** is now **built** (POC 20 — a
  genuine ONNX sentence-encoder behind the `SkillScorer` seam); what it does *not* do is cure the
  near-duplicate shadowing collapse on its own (33% vs lexical 17% / dense 0% at full scale) — the
  real fix is library curation (dedup / lifecycle, POC 18), which the POC 15 canvas now **applies**
  (its retrieval pane drops retired skills and de-ranks degraded ones; a curate re-eval moves a
  skill through the lifecycle). The drag-and-drop UI (POC 15) now also carries a **retrieval
  pane** (curation-aware), an **NL-authoring pane** (POC 2/3), an **eval/refinement pane** (POC 5/9),
  and a **curate re-eval** (POC 18) — a skill can be described, routed, checked/refined, and curated
  on the same canvas.
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
| [[skill-composer]] (15) | a browser drag-and-drop canvas loads the palette, snaps only type-compatible connections, live-validates the chain, and exports the composite over REST — plus a curation-aware **retrieval pane** (POC 18: retired dropped, degraded de-ranked), an **author pane** (POC 2), an **eval/refinement pane** (POC 9), and a **curate re-eval** (POC 18) | the "builder UI" doc 01 names as the missing piece; the author / eval / curate panes are endpoint-gated |
| [[runtime-adapter]] (16) | a GOAP plan plans over, and executes over HTTP, an action running in a separate sidecar process — exactly like a local action | fully deterministic / LLM-free (endpoint only for platform boot); the third of doc 03 §2.3's runtime bridges |
| POC 17 (GLiNER2 skill classification) | a real zero-shot transformer (GLiNER2 over ONNX) scores the query by *meaning* and **breaks the shadowing tie** lexical/dense cannot (strict 100% / matching 83% vs 33%/17% and 0%) | **local-model-gated** (one-time ONNX bundle download; offline tests mock the classifier) |
| POC 18 (skill lifecycle / drift) | a skill's contract is its eval pass-rate; a SkillOps-style proactive re-eval compares content hash + pass-rate against the baseline and moves it through `incubating → active → degraded → retired` (live: 100% → 0% → retired) | content-hash / drift / state-machine half is pure + offline; the live re-eval is endpoint-gated; **applied in the POC 15 canvas** |
| POC 19 (small-LLM skill selection) | a 0.6B model fine-tuned for single-label decisions, served in a Docker container whose lifecycle Spring Boot Dev Services manages, **beats text similarity** (67%/50%) but trails the GLiNER2 transformer (100%/83%) | **docker-gated** (one-time GGUF download; offline tests stub the endpoint) |
| POC 20 (real semantic `EmbeddingService`) | a genuine ONNX sentence-encoder (`all-MiniLM-L6-v2`) behind the `SkillScorer` seam retrieves cleanly at the distinct scale (100% at 191) and beats the lexical/dense stand-ins under shadowing (33%) | **local-model-gated** (one-time ONNX download); does not fully cure the near-duplicate collapse — curation (POC 18) is the real fix |

## Still open

All twenty POCs — and the builder UI — are built, and **every hypothesis gap doc 03 §2 lists is now
closed**: the runtime bridges (POC 7/13/16), the visual editor (POC 15), skill lifecycle / drift
(POC 18), and the real semantic `EmbeddingService` (POC 20). The former follow-ups are now done:

- **Wiring the other POCs into the POC 15 canvas** — done: a curation-aware **retrieval pane** (the
  POC 8/12/20 `SkillScorer` seam), an **NL-authoring pane** (POC 2/3), an **eval/refinement pane**
  (POC 5/9), and a **curate re-eval** (POC 18). A skill can now be described, routed, checked/
  refined, and curated on the same surface.
- **Library curation as the shadowing remedy** — done (2026-11-03): the POC 15 retrieval pane applies
  POC 18's lifecycle state (retired skills dropped, degraded ones de-ranked), and the curate
  re-eval re-scores a skill and moves it through the lifecycle. The pure content-hash / drift /
  state-machine half is offline-testable; the measured score is endpoint-gated.
