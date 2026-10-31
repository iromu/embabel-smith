# POC 1 — No-code runtime (keystone)

The keystone claim: a **YAML skill-pack is ingested at runtime with zero compilation**, and a
single general-purpose agent plans over its exported goal via GOAP.

## What it proves

- **No-compilation ingestion (C1).** A skill-pack is loaded through `YmlStepSpecRepository` +
  `StepSpecAgentScopeBuilder.deploy()` into the live GOAP planner — the same `embabel-agent-spec`
  mechanism the vendor's production "realms" use.
- **An exported goal is a callable skill (C2).** The one `skill-agent` plans over the exported goal
  and returns its output — no sub-agents, no orchestration code.
- The planner is selectable per run (default GOAP), and the model endpoint is fully env-driven.

## How it works

```mermaid
flowchart LR
    subgraph pack["skill-pack (pure-data YAML, zero compilation)"]
        G["goal + steps + types"]
    end
    YSR["YmlStepSpecRepository<br/>+ SkillPackTypeMapper"]
    DEPLOY["StepSpecAgentScopeBuilder.deploy"]
    PLAT["AgentPlatform<br/>GOAP planner"]
    IN["UserInput"]
    OUT["BackgroundMessage"]
    pack --> YSR --> DEPLOY --> PLAT
    IN --> PLAT
    PLAT -->|"plan over the exported goal"| OUT
```

`NoCodeRuntime` boots an `AgentPlatform`, maps the pack's pure-data `DynamicType`s onto Embabel
types (`SkillPackTypeMapper`), ingests the `PromptedActionSpec`/`GoalSpec` step specs from YAML
(`StepSpecAssembler`), and runs a plan from a `UserInput` to the goal's `BackgroundMessage` output
(`SkillRunner`, planner chosen by `PlannerResolver`).

## Run it

Endpoint-gated (needs a live OpenAI-compatible endpoint — see the top-level README "Model
endpoint"):

```bash
BUILDER_PACK_DIR="$PWD/skill-packs/explainer" \
BUILDER_INPUT="Explain how a coffee machine works" BUILDER_PLANNER=GOAP \
    mvn -pl poc/poc-1-no-code-runtime spring-boot:run
```

See `docs/04-poc-run-results.md` for the full run evidence.
