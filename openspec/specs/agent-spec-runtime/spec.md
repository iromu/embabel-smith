# agent-spec-runtime Specification

## Purpose
The product runtime: ingest agent specs from a spec source (folder or git) with zero compilation and run an exported goal over them, with every run recorded as a trace. It is the production counterpart of POC 1, generalized to sources and multi-pack, and instrumented for the self-improvement loop.

## Requirements

### Requirement: Run a goal from a resolved source
The runtime SHALL run a named exported goal over the agent specs resolved from a spec source, binding the user input to the goal's input type and returning the goal's output.

#### Scenario: Run succeeds
- **WHEN** the runtime is given a source containing a pack that exports goal `g` and an input satisfying `g`'s preconditions
- **THEN** the plan executes and the goal's output is returned

#### Scenario: Goal not found
- **WHEN** the requested goal name is not exported by any pack in the source
- **THEN** the run fails with an error listing the goal names that ARE available

#### Scenario: Unmet preconditions
- **WHEN** the input does not satisfy the goal's preconditions
- **THEN** the run fails with a message naming the unmet precondition, and a failure trace is still emitted

### Requirement: Multi-pack ingest
The runtime SHALL ingest all packs of a resolved source into one platform so that steps from different packs can be composed in a single plan.

#### Scenario: Cross-pack plan
- **WHEN** the source contains two packs whose steps chain (pack A's output type feeds pack B's input type)
- **THEN** a goal whose plan spans both packs executes correctly

#### Scenario: Duplicate goal name
- **WHEN** two packs in the source export the same goal name
- **THEN** ingestion reports the collision and names both packs, and the run does not proceed ambiguously

### Requirement: Planner is selectable per run
The runtime SHALL accept a per-run planner selection and SHALL default to GOAP, matching the POC runtime behavior.

#### Scenario: Planner override
- **WHEN** a run specifies a non-default planner
- **THEN** that planner computes the plan

#### Scenario: Default
- **WHEN** no planner is specified
- **THEN** GOAP is used

### Requirement: Trust-tier gating
The runtime SHALL enforce trust tiers as in POC 10: steps declared high-trust (e.g. shell execution) SHALL NOT execute unless the run is explicitly granted that tier.

#### Scenario: High-trust step blocked by default
- **WHEN** a plan includes a high-trust step and the run was not granted the high-trust tier
- **THEN** the step does not execute and the run reports the tier requirement

#### Scenario: Granted tier executes
- **WHEN** the run is explicitly granted the high-trust tier
- **THEN** the high-trust step executes

### Requirement: Every run emits a trace record
The runtime SHALL append one trace record per run to the configured trace store, capturing: run id, timestamp, source URI and resolved ref, pack name and content hash, goal, input, planner, per-step records (step name, status, duration, output excerpt), overall success/failure, error (if any), and the goal output excerpt.

#### Scenario: Successful run recorded
- **WHEN** a run succeeds
- **THEN** the trace store gains a record with `success=true`, the goal output excerpt, and one per-step record per executed step

#### Scenario: Failed run recorded
- **WHEN** a run fails (unmet preconditions, blocked tier, or step error)
- **THEN** the trace store gains a record with `success=false` and the failure reason

#### Scenario: Trace output does not change the run
- **WHEN** tracing is enabled
- **THEN** the run's result is identical to a run with tracing disabled (tracing observes, never intervenes)

### Requirement: Trace store is file-based
The trace store SHALL be pluggable behind a `TraceStore` interface, with the JSONL file store (one JSON object per line, one per run) in a configurable directory as the default implementation, defaulting to `~/.smith/traces/runs.jsonl`; the directory is created on first write. Alternative stores may be provided without changing the trace record shape, and the CLI contract (`--trace-dir`) SHALL keep selecting the JSONL store.

#### Scenario: Append semantics
- **WHEN** two runs complete
- **THEN** the trace file contains exactly two lines, in completion order

#### Scenario: Corrupt line tolerated on read
- **WHEN** the trace file contains a malformed line
- **THEN** readers skip that line and report it as a warning rather than failing

#### Scenario: Default is the JSONL store
- **WHEN** the runtime runs with no alternative trace store configured
- **THEN** traces are written to the JSONL file exactly as before this change

#### Scenario: Alternative store selected
- **WHEN** an alternative `TraceStore` implementation is configured
- **THEN** runs are recorded through it with the same record shape and no change to run behavior

### Requirement: CLI contract
The runtime SHALL provide a CLI: `smith run --source <uri> [--goal <name>] [--input <text>] [--planner <name>] [--trust-tier <tier>] [--trace-dir <dir>]`, printing the goal output on success and a clear error on failure.

#### Scenario: CLI run
- **WHEN** the CLI is invoked with a valid source, goal, and input
- **THEN** it prints the goal output to stdout and exits zero

#### Scenario: CLI failure
- **WHEN** the run fails
- **THEN** it prints the error to stderr and exits non-zero

### Requirement: Offline-testable core, endpoint-gated model
The runtime core (source resolution, ingest, planning, trace emission) SHALL be unit-testable without a model endpoint; only action bodies invoke the model.

#### Scenario: Offline tests pass
- **WHEN** `mvn test` runs on `prod/embabel-smith-runtime`
- **THEN** all offline tests pass with no model endpoint configured

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
