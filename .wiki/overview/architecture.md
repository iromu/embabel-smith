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
updated_at: "2026-11-01"
---

# Architecture and module map

Embabel Smith is a Maven **multi-module** project (design D1). The parent POM (`pom.xml`)
centralizes the Embabel version (1.5.3), the `repo.embabel.com` repository, the Spring Boot BOM
(4.1.1 — the version Embabel 1.5.3 is built against), and the test dependencies. The eleven POCs
live under a `poc/` aggregator (`poc/pom.xml`); each is independently buildable and runnable, so a
failure isolates to one claim.

```
embabel-smith/            parent POM (pom packaging)
├── smith-common/         the skill-pack FORMAT owner — shared by everyone
└── poc/                  aggregator for the POC modules
    ├── poc-1-no-code-runtime       POC 1 — keystone: ingest a pack, run a plan
    ├── poc-2-nl-compile            POC 2 — description → draft
    ├── poc-3-builder-agent         POC 3 — the builder @Agent
    ├── poc-4-skill-format          POC 4 — on-demand load of one exported goal
    ├── poc-5-eval                  POC 5 — score a goal-type skill
    ├── poc-6-skill-composition     POC 6 — GOAP chains two independent packs
    ├── poc-7-runtime-adapter       POC 7 — a skill invokes a CLI (shell tool group)
    ├── poc-8-retrieval             POC 8 — lexical retriever over same-typed skills
    ├── poc-9-quality-refinement    POC 9 — eval-guided prompt rewrite
    ├── poc-10-trust-tiers          POC 10 — per-tier allowlist + input guardrail
    └── poc-11-progressive-disclosure POC 11 — plan context stays constant under load
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

POCs 6–11 are independent consumers of the format, each adding one skills-first capability:
composition ([[skill-composition]]), CLI invocation ([[runtime-adapter]]), retrieval
([[retrieval-shadowing]]), eval-guided refinement ([[eval-guided-refinement]]), trust tiers
([[trust-tiers]]), and progressive disclosure ([[progressive-disclosure]]).

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
