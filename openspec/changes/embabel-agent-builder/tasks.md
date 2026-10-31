# Tasks

Endpoint-gated tasks are marked **(needs live endpoint)** — they build and scaffold without a model endpoint, but the end-to-end run is gated on one (see design.md Open Questions). All other tasks verify offline.

## 1. Foundation (parent POM + `smith-common`)

- [x] 1.1 Create the parent POM (Embabel 1.5.3, Java 21, `repo.embabel.com` repository, OpenAI-custom model config via env vars) and the empty submodule stubs; verify `mvn -q validate` resolves the Embabel dependencies from `repo.embabel.com`
- [x] 1.2 Implement the skill-pack data model + YAML (de)serialization in `smith-common` (manifest, action-step, goal, type); verify a round-trip test writes then reads back a sample skill-pack losslessly
- [x] 1.3 Add skill-pack format validation in `smith-common` (layout, exactly-one exported goal, prompt references resolve, type references resolve); verify it accepts a well-formed pack and rejects a pack with a dangling type reference
- [x] 1.4 Document the skill-pack format in `docs/skill-pack-format.md` and the module map + per-POC run commands in a top-level `README.md`; verify the documented run commands are syntactically valid Maven invocations

## 2. POC 1 — no-code runtime (keystone)

- [x] 2.1 Build `poc-1-no-code-runtime`: a launcher that boots an `AgentPlatform`, ingests a sample skill-pack via `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()`; verify it boots and the exported goal is present on the platform **(needs live endpoint)**
- [x] 2.2 Wire a GOAP plan + `runAgentFrom` over the exported goal with a user input; verify a goal output (`BackgroundMessage`) is returned end-to-end **(needs live endpoint)**
- [x] 2.3 Make the planner selectable per run (default GOAP); verify overriding the planner changes the computed plan for the same input
- [x] 2.4 Confirm the model endpoint is fully env-driven (base-url / key / model list); verify starting without a resolvable endpoint yields a clear configuration error, not an opaque model error

## 3. POC 2 — NL-compile

- [x] 3.1 Build `poc-2-nl-compile`: call `NaturalLanguageCompiler.compileAgent` on a description; verify it returns an `AgentCompilationResult` with ≥1 action, an exported goal, and the intermediate types the chain requires **(needs live endpoint)**
- [x] 3.2 Verify an ambiguous description returns a draft plus explicit issues to review, rather than a silently-wrong draft **(needs live endpoint)**

## 4. POC 4 — skill format / on-demand load

- [x] 4.1 In `poc-4-skill-format`, load a single exported goal on demand and plan over its action chain; verify the plan resolves to the goal's output type
- [x] 4.2 Verify a goal declared `export: false` is not exposed as a callable skill

## 5. POC 3 — the builder agent

- [x] 5.1 Build the builder `@Agent` (GOAP, single goal "a validated skill-pack exists on disk") with code-backed actions; verify GOAP plans the compile → validate → write sequence by matching typed preconditions
- [x] 5.2 Implement `compileFromDescription` (wraps the NL compiler); verify it emits an `AgentCompilationResult` from a description **(needs live endpoint)**
- [x] 5.3 Implement `validateDraft` (deploy to a throwaway platform, check bindings/schemas); verify an invalid draft is rejected and nothing is written to the skill-pack
- [x] 5.4 Implement `writeSkillPack` (emit via `smith-common`); verify the written directory conforms to the skill-pack format by reusing the group-1 validation
- [x] 5.5 End-to-end: description → written skill-pack; verify the written pack is ingestable by `poc-1-no-code-runtime` **(needs live endpoint)**

## 6. POC 5 — eval

- [x] 6.1 Wire `embabel-agent-eval` for a goal-type (non-chat) skill via `AgentInvocation`; verify it produces a composite score for the generated skill **(needs live endpoint)**
- [x] 6.2 Implement prompt rewrite from a failing task + re-validate; verify a failing task yields a revised prompt and the revised draft re-validates **(needs live endpoint, stretch)**

## 7. Integration

- [ ] 7.1 Verify the full loop: run `poc-3-builder-agent` on a description, then run the produced skill-pack in `poc-1-no-code-runtime`; verify the runtime returns a goal output for the built skill **(needs live endpoint)**
- [x] 7.2 Verify `mvn -q verify` passes across all modules and each POC's offline tests are green

## Workflow follow-up

- Confirm the live model endpoint (base URL / key / model list) before running the endpoint-gated tasks.
- Archive the change after the POCs are reviewed and the endpoint-gated runs are confirmed.
