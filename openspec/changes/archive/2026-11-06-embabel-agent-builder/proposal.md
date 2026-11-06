# Proposal

## Why

We want to build agentic systems by *composing skills on one general-purpose Embabel agent* rather than hand-crafting a bespoke agent per capability. Embabel 1.5.3 already ships the declarative, no-compilation ingestion engine (`embabel-agent-spec`) and a natural-language-to-spec compiler, but nothing wires them into a **builder**: an Embabel agent that takes a plain-language description and *emits a deployable, no-code skill-pack*. This change proves that loop end-to-end with POC submodules before committing to the full product.

## What Changes

- Introduce a **Maven multi-module** project (parent POM, Embabel 1.5.3, Java 21) whose submodules are POCs, each de-risking one claim from the research docs.
- Define a **skill-pack format** — a directory of pure YAML (`manifest.yml`, `steps/*.yml` as `PromptedActionSpec`, `goal.yml` as `GoalSpec` with `export: true`, `types/*.yml` as `DynamicType`) that the builder emits and the runtime ingests.
- Build a **fixed no-code runtime** (`poc-1-no-code-runtime`): a launcher that boots an `AgentPlatform`, ingests a skill-pack via `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()`, and runs a plan over the exported goal — no compilation.
- Build the **builder as an Embabel agent** (`poc-3-builder-agent`): a single GOAP-planned `@Agent` whose code-backed actions compile a description into a draft skill (`NaturalLanguageCompiler`), validate it against a throwaway platform, write the skill-pack to disk, and score/rewrite prompts via `embabel-agent-eval`.
- De-risk the **NL→draft** path (`poc-2-nl-compile`) and the **eval/rewrite** path (`poc-5-eval`) as independent POCs.

**Out of scope for this pass** (deferred to a later change): `SkillRegistry` (semantic retrieval, trust tiers), `SkillComposer` (drag canvas), CLI/remote/MCP runtime adapters, and the full multi-pane builder UI.

## Capabilities

### New Capabilities
- `skill-pack-format`: The shared YAML contract that defines what a deployable skill-pack is — the exported-goal unit, its action steps, intermediate `DynamicType`s, and manifest. Both the builder (producer) and the runtime (consumer) honor it.
- `skill-pack-runtime`: The fixed no-code runtime that ingests a skill-pack and runs it on an Embabel `AgentPlatform` with zero compilation, a selectable planner, and any OpenAI-compatible model endpoint.
- `skill-builder-agent`: The Embabel meta-agent that turns a natural-language description into a validated, on-disk skill-pack (compile → validate → write → eval/rewrite).

### Modified Capabilities
(none — this project has no existing specs)

## Impact

- **New code**: a greenfield Maven multi-module tree under the project root (parent POM + `smith-common` + POC submodules). No existing code is modified.
- **Dependencies**: Embabel 1.5.3 (`embabel-agent-starter`, `embabel-agent-starter-openai`, `embabel-agent-spec`, `embabel-agent-eval`) from `repo.embabel.com`; Java 21.
- **External system**: a live model endpoint (any OpenAI-compatible — LiteLLM / OpenAI / Ollama) is required to actually *run* the POCs; the endpoint is pluggable via env vars so POCs can be scaffolded before the endpoint is fixed.
- **Risk**: `embabel-agent-eval` is documented as conversational/chatbot-oriented; adapting it to goal-type (non-chat) agents is a known gap that the eval POC must prove or work around.
