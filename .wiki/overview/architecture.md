---
title: "Architecture and module map"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "pom.xml"
  - "poc/pom.xml"
  - "prod/pom.xml"
  - "docs/04-poc-run-results.md"
  - "openspec/changes/embabel-agent-builder/design.md"
updated_at: "2026-11-06"
---

# Architecture and module map

Embabel Smith is a Maven **multi-module** project (design D1). The parent POM (`pom.xml`)
centralizes the Embabel version (1.5.4), the `repo.embabel.com` repository, the Spring Boot BOM,
and the test dependencies. There are two top-level trees: the POCs under a `poc/` aggregator
(`poc/pom.xml`) — each independently buildable and runnable, so a failure isolates to one claim —
and the product under a `prod/` aggregator (`prod/pom.xml`) — the four product modules that
extract proven POC logic (see [[product-phase]]).

```
embabel-smith/            parent POM (pom packaging)
├── smith-common/         the agent spec FORMAT owner — shared by everyone
├── prod/                 aggregator for the product modules (extract, never depend on POCs)
│   ├── embabel-smith-specsource    folder:/git: spec sources + content hash (change 02)
│   ├── embabel-smith-runtime        the runtime product: smith run CLI + JSONL trace (change 03)
│   ├── embabel-smith-builder        the builder product: smith build CLI + pipeline (change 04)
│   └── embabel-smith-studio         the self-improving studio (empty skeleton, changes 05–06)
└── poc/                  aggregator for the POC modules
    ├── poc-1-no-code-runtime          POC 1 — keystone: ingest a pack, run a plan
    ├── poc-2-nl-compile               POC 2 — description → draft
    ├── poc-3-builder-agent            POC 3 — the builder @Agent
    ├── poc-4-agent-spec-format             POC 4 — on-demand load of one exported goal
    ├── poc-5-eval                     POC 5 — score a goal-type spec
    ├── poc-6-agent-spec-composition        POC 6 — GOAP chains two independent packs
    ├── poc-7-runtime-adapter          POC 7 — a spec invokes a CLI (shell tool group)
    ├── poc-8-retrieval                POC 8 — lexical retriever over same-typed specs
    ├── poc-9-quality-refinement       POC 9 — eval-guided prompt rewrite
    ├── poc-10-trust-tiers             POC 10 — per-tier allowlist + input guardrail
    ├── poc-11-progressive-disclosure  POC 11 — plan context stays constant under load
    ├── poc-12-semantic-retrieval      POC 12 — retrieval at 200+ scale + the shadowing failure
    ├── poc-13-remote-mcp              POC 13 — a spec published + invoked as a remote MCP tool
    ├── poc-14-composer                POC 14 — the spec Composer (headless canvas + composite)
    ├── poc-15-composer-ui             POC 15 — the visual builder UI (canvas + panes)
    ├── poc-16-remote-action           POC 16 — the remote-action bridge (GOAP over HTTP)
    ├── poc-17-gliner-retrieval        POC 17 — GLiNER2 transformer spec classification
    ├── poc-18-agent-spec-lifecycle         POC 18 — spec lifecycle / drift detection
    ├── poc-19-small-llm-selector      POC 19 — small-LLM spec selection (Docker-served)
    ├── poc-20-semantic-embedding      POC 20 — the real semantic EmbeddingService
    ├── poc-21-agent-spec-security          POC 21 — install-time spec security / vulnerability scanning
    ├── poc-22-unified-ui              POC 22 — the unified builder UI (Vue 3 + Quasar SPA)
    └── poc-23-remote-embedding-scale  POC 23 — remote embedding selection at scale (to 1000 specs)
```

## The dependency shape

The key structural fact: **`smith-common` is the hub**. It is the only module that owns the
agent spec format (data model, YAML I/O, validation) and it is **framework-free** — no Embabel or
Spring imports, so it is fully offline-testable. Every POC depends on it. The POCs are where the
format meets Embabel's runtime types; `smith-common` never knows Embabel exists.

- **POC 1** (`poc-1-no-code-runtime`) — the keystone. Consumes the format, maps it onto Embabel
  `DynamicType`/`DomainType`, ingests via `YmlStepSpecRepository`, plans and runs. See
  [[runtime-run]].
- **POC 3** (`poc-3-builder-agent`) — produces the format. Wraps Embabel's NL compiler, then
  validates and writes using `smith-common`. See [[builder-pipeline]].
- **POC 5** (`poc-5-eval`) — reuses POC 1's `StepSpecAssembler` and `AgentSpecTypeMapper` (it
  depends on `poc-1-no-code-runtime`) to deploy the target spec, then scores it via
  `AgentInvocation`. See [[eval-scoring]].
- **POC 2** (`poc-2-nl-compile`) — de-risks the NL→draft path in isolation. See [[nl-compile]].
- **POC 4** (`poc-4-agent-spec-format`) — proves the exported-goal-as-spec primitive and on-demand
  loading. See [[spec-format-on-demand]].

POCs 6–23 are independent consumers of the format, each adding one GOAP-based capability:
composition ([[spec-composition]]), CLI invocation ([[runtime-adapter]]), retrieval
([[retrieval-shadowing]]), eval-guided refinement ([[eval-guided-refinement]]), trust tiers
([[trust-tiers]]), progressive disclosure ([[progressive-disclosure]]), semantic retrieval at scale
(POC 12), remote/MCP invocation ([[runtime-adapter]]), the spec Composer and the visual builder UI
([[spec-composer]]), the remote-action bridge ([[runtime-adapter]]), GLiNER2 classification
([[shadowing-remedies]]), spec lifecycle / drift ([[spec-lifecycle]], applied in the POC 15
canvas), small-LLM selection ([[shadowing-remedies]]), the real semantic `EmbeddingService`
([[shadowing-remedies]]), install-time security scanning ([[spec-security-scanning]]), the unified
builder UI (POC 22, a Vue 3 + Quasar SPA over the POC 15 REST surface), and remote embedding
selection at scale (POC 23, [[shadowing-remedies]]). See [[what-the-pocs-prove]] for the per-POC
verdicts.

**The product modules** (`prod/`) are a *second* set of consumers of the same format, but with a
harder rule: they depend on `smith-common` and Embabel directly, **never** on a `poc-*` artifact
(enforced by the enforcer ban in `prod/pom.xml`). `specsource` is framework-free like
`smith-common`; `runtime` and `builder` are Spring Boot CLI apps that reuse the same ingest/build
seams the POCs proved. See [[product-phase]] for the six changes that build them.

## The two "seams"

Two classes are the deliberate boundaries between the format and the framework:

- `poc/poc-1-no-code-runtime/.../AgentSpecTypeMapper.java` — maps pure-data `DynamicTypeDef`s to
  Embabel `DynamicType`s so the platform's data dictionary knows the pack's intermediate types.
- `poc/poc-3-builder-agent/.../DraftToAgentSpec.java` — maps Embabel's NL-compiler output
  (`AgentCompilationResult`) back into the format's `AgentSpec`, so the builder's downstream steps
  operate on the format's own types.

POCs 6, 7, 9, and 10 repeat the same `DynamicTypeDef → DynamicType` mapping inline (a small
`runtimeTypes(pack)` helper each) rather than centralizing it — the seam *pattern* is reused, not
the class. Keeping Embabel-specific types confined to these helpers (and the endpoint-gated
compile step) is what preserves the "format is pure data" invariant. See [[agent-spec-model]] and
[[no-code-ingestion]].

## How it fits Embabel's model

The runtime seam is: `DynamicTypeDef` → Embabel `DynamicType`; `ActionStepSpec`/`GoalStepSpec` →
Embabel `PromptedActionSpec`/`GoalSpec`; ingested via `YmlStepSpecRepository` +
`StepSpecAgentScopeBuilder.deploy()` (no compilation). The exported goal's `outputTypeName` is
the type a run returns. See [[exported-goal-as-spec]] and [[no-code-ingestion]].
