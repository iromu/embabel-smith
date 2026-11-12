# Spec Delta

## ADDED Requirements

### Requirement: Model endpoint is pluggable
The runtime SHALL source its language model from any OpenAI-compatible endpoint configured via
environment variables (base URL, API key, model list), without code changes.

#### Scenario: Endpoint via environment
- **WHEN** the runtime is started with base-URL, API-key, and model-list environment variables set
- **THEN** it routes model calls to that endpoint

#### Scenario: Missing endpoint configuration
- **WHEN** the runtime is started without a resolvable model endpoint
- **THEN** it fails with a clear configuration error rather than an opaque model error

### Requirement: Deterministic plan structure
The runtime SHALL compute the plan with a non-model planner so that only action bodies invoke
the model, making the sequence of steps reproducible for a given agent spec and input.

#### Scenario: Same input yields same plan structure
- **WHEN** the runtime runs the same agent spec with the same input twice
- **THEN** the sequence of planned steps is the same in both runs (model output content may differ)
