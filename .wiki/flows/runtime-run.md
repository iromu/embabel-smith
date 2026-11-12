---
title: "Runtime run flow (POC 1)"
type: "flow"
status: "active"
language: "default"
source_paths:
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/NoCodeRuntime.java"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SpecRunner.java"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/PlannerResolver.java"
updated_at: "2026-11-01"
---

# Runtime run flow (POC 1)

POC 1 is the **keystone**: the fixed no-code runtime ingests an agent spec from YAML with zero
compilation and runs a plan over its exported goal. `NoCodeRuntime` is the Spring Boot launcher;
`SpecRunner` does the work. Entry point:
`poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SpecRunner.java`.

## Step by step

1. **Check the endpoint.** `NoCodeRuntime.run()` first calls
   `ModelEndpointConfig.configError()` and throws if unresolvable — the clear-configuration-error
   contract. See [[model-endpoint]].
2. **Load and validate.** `SpecRunner.run()` reads the pack with `AgentSpecReader` and validates
   it with `AgentSpecValidator`. An invalid pack throws before anything runs. See
   [[agent-spec-format]] and [[validation-codes]].
3. **Register the types.** `AgentSpecTypeMapper.allRuntimeTypes(pack)` maps the pack's
   `DynamicTypeDef`s to Embabel `DynamicType`s (plus the built-in `UserInput` /
   `BackgroundMessage`), registered via `DataDictionary.fromDomainTypes("spec-types", …)`. See
   [[no-code-ingestion]].
4. **Assemble the flat step directory.** `StepSpecAssembler` copies `goal.yml` and every
   `steps/*.yml` into a fresh temp directory so `YmlStepSpecRepository` can load the goal and its
   action chain together. The source pack is untouched.
5. **Deploy.** `StepSpecAgentScopeBuilder(...).deploy()` ingests the YAML (no compilation) and
   produces an `AgentScope`; `scope.createAgent(...)` + `platform.deploy(agent)` register the
   agent on the `AgentPlatform`.
6. **Resolve the planner.** `PlannerResolver` picks the planner with precedence:
   per-run override (e.g. `-Dbuilder.planner`) → the pack's `manifest.defaultPlanner` →
   **`GOAP`** (the deterministic default). Unknown names throw with the supported list.
7. **Run the plan.** `platform.runAgentFrom(agent, options, Map.of("userInput", new UserInput(input)))`
   plans from `UserInput` to the goal's output type and executes it. **Only the action bodies call
   the model**; planning itself is the non-model GOAP planner, so the step sequence is
   reproducible.
8. **Extract the goal output.** `process.lastResult()` is expected to be a `BackgroundMessage`;
   its `content` is returned. If it isn't, the runtime throws with the planner and process status
   (e.g. the input didn't satisfy the spec's preconditions).

## Why GOAP by default

GOAP (Goal-Oriented Action Planning) plans over typed pre/postconditions without a model, so the
sequence is deterministic and only the LLM-backed action prompts vary. This keeps the *structure*
of a run reproducible while the *content* stays model-generated — the same principle the builder
uses. See [[exported-goal-as-spec]] and [[builder-pipeline]].
