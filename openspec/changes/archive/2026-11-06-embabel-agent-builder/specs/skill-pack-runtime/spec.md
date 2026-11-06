# Spec Delta

## Purpose

The fixed, no-code runtime that ingests a skill-pack and runs it on an Embabel agent platform with zero compilation. It is the only compiled artifact in the system; everything it executes is data (the skill-pack YAML), so a skill-pack can be dropped in and run without a build step.

## ADDED Requirements

### Requirement: Ingest skill-pack without compilation
The runtime SHALL load a skill-pack's action steps and goal from YAML at startup and register them with the agent platform without compiling any agent code.

#### Scenario: Boot from skill-pack
- **WHEN** the runtime starts with a valid skill-pack on disk
- **THEN** the skill-pack's actions and exported goal are available to the platform without any build step

#### Scenario: Malformed YAML at startup
- **WHEN** the runtime starts and a skill-pack file is malformed or fails to parse
- **THEN** startup fails with an error that names the offending file

### Requirement: Run a plan over the exported goal
Given a user input, the runtime SHALL plan and execute a path from the input to the exported goal's output type, and return the goal's output as the result.

#### Scenario: Successful plan produces goal output
- **WHEN** the runtime is given an input that satisfies the skill's preconditions
- **THEN** it executes the planned steps and returns the exported goal's output type as the result

#### Scenario: No plan possible
- **WHEN** the runtime is given an input for which no path to the goal exists
- **THEN** it reports the unmet precondition instead of hanging or returning a silent null

### Requirement: Planner is selectable per run
The runtime SHALL allow the planner to be selected per run from the supported planner set, and SHALL default to GOAP when none is specified.

#### Scenario: Planner overridden
- **WHEN** the runtime is configured to use a non-default planner for a run
- **THEN** that planner is used to compute the plan for the run

#### Scenario: Default planner
- **WHEN** no planner is specified
- **THEN** the runtime plans with GOAP

### Requirement: Model endpoint is pluggable
The runtime SHALL source its language model from any OpenAI-compatible endpoint configured via environment variables (base URL, API key, model list), without code changes.

#### Scenario: Endpoint via environment
- **WHEN** the runtime is started with base-URL, API-key, and model-list environment variables set
- **THEN** it routes model calls to that endpoint

#### Scenario: Missing endpoint configuration
- **WHEN** the runtime is started without a resolvable model endpoint
- **THEN** it fails with a clear configuration error rather than an opaque model error

### Requirement: Deterministic structure, model-generated content
The runtime SHALL compute the plan with a non-model planner so that only action bodies invoke the model, making the sequence of steps reproducible for a given skill-pack and input.

#### Scenario: Same input yields same plan structure
- **WHEN** the runtime runs the same skill-pack with the same input twice
- **THEN** the sequence of planned steps is the same in both runs (model output content may differ)
