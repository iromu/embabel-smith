---
title: "Architecture and module map"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "pom.xml"
  - "poc/pom.xml"
  - "docs/04-poc-run-results.md"
  - "openspec/changes/embabel-agent-builder/design.md"
updated_at: "2026-11-04"
---

# Architecture and module map

Embabel Smith is a Maven **multi-module** project (design D1). The parent POM (`pom.xml`)
centralizes the Embabel version (1.5.3), the `repo.embabel.com` repository, the Spring Boot BOM
(4.1.1 — the version Embabel 1.5.3 is built against), and the test dependencies. The POCs live
under a `poc/` aggregator (`poc/pom.xml`); each is independently buildable and runnable, so a
failure isolates to one claim.

```
embabel-smith/            parent POM (pom packaging)
├── smith-common/         the skill-pack FORMAT owner — shared by everyone
└── poc/                  aggregator for the POC modules
    ├── poc-1-no-code-runtime          POC 1 — keystone: ingest a pack, run a plan
    ├── poc-2-nl-compile               POC 2 — description → draft
    ├── poc-3-builder-agent            POC 3 — the builder @Agent
    ├── poc-4-skill-format             POC 4 — on-demand load of one exported goal
    ├── poc-5-eval                     POC 5 — score a goal-type skill
    ├── poc-6-skill-composition        POC 6 — GOAP chains two independent packs
    ├── poc-7-runtime-adapter          POC 7 — a skill invokes a CLI (shell tool group)
    ├── poc-8-retrieval                POC 8 — lexical retriever over same-typed skills
    ├── poc-9-quality-refinement       POC 9 — eval-guided prompt rewrite
    ├── poc-10-trust-tiers             POC 10 — per-tier allowlist + input guardrail
    ├── poc-11-progressive-disclosure  POC 11 — plan context stays constant under load
    ├── poc-12-semantic-retrieval      POC 12 — retrieval at 200+ scale + the shadowing failure
    ├── poc-13-remote-mcp              POC 13 — a skill published + invoked as a remote MCP tool
    ├── poc-14-composer                POC 14 — the skill Composer (headless canvas + composite)
    ├── poc-15-composer-ui             POC 15 — the visual builder UI (canvas + panes)
    ├── poc-16-remote-action           POC 16 — the remote-action bridge (GOAP over HTTP)
    ├── poc-17-gliner-retrieval        POC 17 — GLiNER2 transformer skill classification
    ├── poc-18-skill-lifecycle         POC 18 — skill lifecycle / drift detection
    ├── poc-19-small-llm-selector      POC 19 — small-LLM skill selection (Docker-served)
    ├── poc-20-semantic-embedding      POC 20 — the real semantic EmbeddingService
    ├── poc-21-skill-security          POC 21 — install-time skill security / vulnerability scanning
    └── poc-22-unified-ui              POC 22 — the unified builder UI (Vue 3 + Quasar SPA, in progress)
```

## The dependency shape

The key structural fact: **`smith-common` is the hub**. It is the only module that owns the
skill-pack format (data model, YAML I/O, validation) and it is **framework-free** — no Embabel or
Spring imports, so it is fully offline-testable. Every POC depends on it. The POCs are where the
format meets Embabel's runtime types; `smith-common` never knows Embabel exists.

- **POC 1** (`poc-1-no-code-runtime`) — the keystone. Consumes the format, maps it onto Embabel
  `DynamicType`/`DomainType`, ingests via `YmlStepSpecRepository`, plans and runs. See
  [[runtime-run]].
- **POC 3** (`poc-3-builder-agent`) — produces the format. Wraps Embabel's NL compiler, then
  validates and writes using `smith-common`. See [[builder-pipeline]].
- **POC 5** (`poc-5-eval`) — reuses POC 1's `StepSpecAssembler` and `SkillPackTypeMapper` (it
  depends on `poc-1-no-code-runtime`) to deploy the target skill, then scores it via
  `AgentInvocation`. See [[eval-scoring]].
- **POC 2** (`poc-2-nl-compile`) — de-risks the NL→draft path in isolation. See [[nl-compile]].
- **POC 4** (`poc-4-skill-format`) — proves the exported-goal-as-skill primitive and on-demand
  loading. See [[skill-format-on-demand]].

POCs 6–21 are independent consumers of the format, each adding one skills-first capability:
composition ([[skill-composition]]), CLI invocation ([[runtime-adapter]]), retrieval
([[retrieval-shadowing]]), eval-guided refinement ([[eval-guided-refinement]]), trust tiers
([[trust-tiers]]), progressive disclosure ([[progressive-disclosure]]), semantic retrieval at scale
(POC 12), remote/MCP invocation ([[runtime-adapter]]), the skill Composer and the visual builder UI
([[skill-composer]]), the remote-action bridge ([[runtime-adapter]]), GLiNER2 classification
([[shadowing-remedies]]), skill lifecycle / drift ([[skill-lifecycle]], applied in the POC 15
canvas), small-LLM selection ([[shadowing-remedies]]), the real semantic `EmbeddingService`
([[shadowing-remedies]]), and install-time security scanning ([[skill-security-scanning]]). See
[[what-the-pocs-prove]] for the per-POC verdicts.

## The two "seams"

Two classes are the deliberate boundaries between the format and the framework:

- `poc/poc-1-no-code-runtime/.../SkillPackTypeMapper.java` — maps pure-data `DynamicTypeDef`s to
  Embabel `DynamicType`s so the platform's data dictionary knows the pack's intermediate types.
- `poc/poc-3-builder-agent/.../DraftToSkillPack.java` — maps Embabel's NL-compiler output
  (`AgentCompilationResult`) back into the format's `SkillPack`, so the builder's downstream steps
  operate on the format's own types.

POCs 6, 7, 9, and 10 repeat the same `DynamicTypeDef → DynamicType` mapping inline (a small
`runtimeTypes(pack)` helper each) rather than centralizing it — the seam *pattern* is reused, not
the class. Keeping Embabel-specific types confined to these helpers (and the endpoint-gated
compile step) is what preserves the "format is pure data" invariant. See [[skill-pack-model]] and
[[no-code-ingestion]].

## How it fits Embabel's model

The runtime seam is: `DynamicTypeDef` → Embabel `DynamicType`; `ActionStepSpec`/`GoalStepSpec` →
Embabel `PromptedActionSpec`/`GoalSpec`; ingested via `YmlStepSpecRepository` +
`StepSpecAgentScopeBuilder.deploy()` (no compilation). The exported goal's `outputTypeName` is
the type a run returns. See [[exported-goal-as-skill]] and [[no-code-ingestion]].
