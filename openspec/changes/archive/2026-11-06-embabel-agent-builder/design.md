# Design

## Context

See proposal.md for motivation. The research docs (`docs/embabel-agent-builder-research.md`, `docs/embabel-skills-agent-builder-design.md`) establish the load-bearing facts this design relies on, all verified against Embabel 1.5.4:

- `embabel-agent-spec` is **stable** in 1.5.4 and ingests agents from YAML at runtime (`YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()`), with no compilation.
- `PromptedActionSpec` / `GoalSpec` / `DynamicType` are the YAML-serializable step types; `GoalSpec.export: true` is the "skill" primitive.
- `DefaultNaturalLanguageCompiler` turns a description into an `AgentCompilationResult` (actions + goal + intermediate types + errors).
- `embabel-agent-eval` is conversational/chatbot-oriented and needs `AgentInvocation` wiring for goal-type agents (known gap).

Constraints: greenfield project (no existing code); Java 21; Embabel 1.5.4 from `repo.embabel.com`; a live model endpoint is required to *run* (not to scaffold) the POCs.

## Goals / Non-Goals

**Goals:**
- Prove, with POC submodules, that an Embabel agent can generate a no-code Embabel runtime (a skill-pack) end-to-end.
- Fix the skill-pack format as a stable shared contract (`smith-common`).
- Keep the builder's *structure* deterministic (GOAP-planned) while its *content* is model-generated.

**Non-Goals (this pass):**
- `SkillRegistry` (semantic retrieval, trust tiers), `SkillComposer` (drag canvas), CLI/remote/MCP runtime adapters, and the multi-pane builder UI.
- Making the skill-pack portable to non-Embabel harnesses (e.g. agentskills.io `SKILL.md`) — native Embabel exported-goal only.

## Decisions

### D1 — Maven multi-module layout, POCs as submodules
```
embabel-smith/                 parent POM: embabel 1.5.4, Java 21, repo.embabel.com
├── smith-common/              skill-pack data model + YAML I/O (the shared contract)
└── poc/                       aggregator (embabel-smith-poc); POCs 1-11 as submodules
    ├── poc-1-no-code-runtime/      POC 1: fixed launcher ingests a skill-pack, runs a plan
    ├── poc-2-nl-compile/           POC 2: description -> AgentCompilationResult
    ├── poc-3-builder-agent/        POC 3: the builder @Agent (compile->validate->write->eval)
    ├── poc-4-skill-format/         POC 4: runtime loads an exported goal on demand
    ├── poc-5-eval/                 POC 5: eval scores a skill; prompt rewrite from failures
    ├── poc-6-skill-composition/    POC 6: compose two skills via GOAP
    ├── poc-7-runtime-adapter/      POC 7: skill invokes a CLI via a shell tool group
    ├── poc-8-retrieval/            POC 8: lexical skill retrieval / shadowing
    ├── poc-9-quality-refinement/   POC 9: eval-guided prompt refinement
    ├── poc-10-trust-tiers/         POC 10: trust tiers + guardrails
    └── poc-11-progressive-disclosure/ POC 11: progressive-disclosure stress test
```
The parent POM centralizes the Embabel version, the `repo.embabel.com` repository, and the OpenAI-compatible model config. Each POC is independently buildable and runnable, so a failure isolates to one claim.
*Alternative rejected:* a single module with test packages — couples the de-risking and hides which claim fails.

### D2 — The builder is a code-backed `@Agent`, not LLM-backed actions
The builder is one `@Agent`, GOAP-planned, single goal ("a validated skill-pack exists on disk"). Its four actions are **code-backed** methods; the model is invoked *inside* two of them:

```
UserInput
  -> compileFromDescription   (code; wraps NaturalLanguageCompiler)  -> AgentCompilationResult
  -> validateDraft            (code; deploy to throwaway platform)    -> ValidationResult
  -> writeSkillPack           (code; emit steps/ + types/ + manifest) -> SkillPackRef
  -> runEvalAndRewrite        (code; eval + model rewrite, loops back)
```
Rationale: the downstream steps depend on the typed `AgentCompilationResult` / `ValidationResult` contract. Code-backed actions preserve that contract and keep the *pipeline order* deterministic (GOAP matches typed pre/postconditions), while the *emitted prompts* remain model-generated.
*Alternative rejected:* LLM-backed actions — loses the typed contract between steps and makes the pipeline order model-dependent, which is exactly the non-determinism we want to avoid in a builder.

### D3 — `smith-common` owns the skill-pack format
The skill-pack data model (manifest, action-step, goal, type) and its YAML (de)serialization live in `smith-common`. Both the builder (producer) and the runtime (consumer) depend on it, so the format has one owner and both sides are checked against the same schema. Field names follow the Embabel spec YAML (`stepType`, `export`, `inputTypeNames`, `outputTypeName`, `prompt`, `ownProperties`).
*Alternative rejected:* let the builder and runtime each parse YAML independently — drift between producer and consumer.

### D4 — Model endpoint is pluggable via environment
No endpoint is hardcoded. The OpenAI-compatible base URL / API key / model list come from environment variables (Embabel's `openai.custom` config), so POCs can be scaffolded and built before an endpoint is fixed, and pointed at LiteLLM / OpenAI / Ollama at run time.
*Alternative rejected:* a fixed endpoint in a committed config — leaks credentials and blocks running in other environments.

### D5 — Validate-before-write is mandatory
Because the NL compiler is model-driven and can emit wrong bindings/schemas, `writeSkillPack` MUST run only after `validateDraft` passes (deploy to a throwaway platform, check bindings and schemas). This is the guardrail that makes "generate with no code" safe.

### D6 — Eval is adapted, not assumed
`embabel-agent-eval` targets chatbots. POC 5 wires `AgentInvocation` so a goal-type (non-chat) skill can be scored. If the rewrite-loop proves too costly, it is demoted to a stretch goal; the score itself is the primary deliverable.

## Risks / Trade-offs

- [1.5.4 API may differ from the docs' javap signatures] → POC 1 runs first and verifies the ingest/deploy/run path before anything else; isolate the builder's spec layer behind an interface so a 2.0 shift is contained.
- [NL compilation emits wrong bindings/schemas] → mandatory validate-before-write (D5); drafts surface issues for review instead of silently guessing.
- [Eval module is chatbot-oriented] → POC 5 proves the `AgentInvocation` adaptation or works around it; rewrite-loop treated as stretch.
- [No live model endpoint available] → POCs build and scaffold to the pluggable env config (D4); end-to-end runs are gated on an endpoint and reported as such, not assumed.
- [DynamicType agents lose compile-time type safety] → accepted; planning still works over type names + conditions, and the format is validated structurally by `smith-common`.

## Migration Plan

Greenfield — no migration. Rollout order follows the POC dependency chain: build the parent POM + `smith-common` first, then POC 1 (keystone), then POC 2/4, then POC 3 (the builder), then POC 5. Each POC is additive; a failing POC does not block the modules that don't depend on it.

## Open Questions

- **Live model endpoint** (base URL / key / model list) — deferrable; the pluggable env config (D4) means it does not change the specs, the approach, or the task breakdown. Confirm before the end-to-end run tasks.
- **Builder invocation surface for the POC** — in-process `main()` vs a thin CLI. Does not change the specs; decide during POC 3.
