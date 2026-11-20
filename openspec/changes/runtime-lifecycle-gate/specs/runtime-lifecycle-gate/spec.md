# Spec Delta

## Purpose

Which published packs the no-code runtime will execute: where a pack's lifecycle state is read from,
which state stops a run, what a blocked run reports and records, and how a pack with no recorded state is
treated.

## ADDED Requirements

### Requirement: A retired pack is not executed
The runtime SHALL NOT execute a pack whose lifecycle sidecar declares it retired, and SHALL exclude it
when ingesting a source so the planner cannot route through it either.

#### Scenario: One retired pack in a multi-pack source
- **WHEN** a source contains a retired pack beside packs that are not retired
- **THEN** the retired pack is not deployed and the run proceeds over the remaining packs

#### Scenario: Running a goal that only the retired pack exported
- **WHEN** the requested goal was exported only by a retired pack
- **THEN** the run fails with a named lifecycle block rather than executing

#### Scenario: A live goal composed through a retired pack
- **WHEN** a live pack's goal would need an action from a retired pack to reach its plan
- **THEN** the plan cannot use that pack, because it was never deployed

### Requirement: A lifecycle block is recognisable as a policy outcome
A run blocked by lifecycle state SHALL report a stable, named reason in its failure and SHALL still emit a
trace record, so that neither a person nor the improvement loop reads it as a defect the spec could be
refined to fix.

#### Scenario: Blocked run recorded
- **WHEN** a run is blocked because the goal's pack is retired
- **THEN** a trace record is appended with no executed steps and an error naming the lifecycle block and
  the pack

#### Scenario: The loop reads the trace window
- **WHEN** the improvement loop evaluates a window whose failures are lifecycle blocks
- **THEN** it does not queue a refinement for that pack

#### Scenario: A typo in the goal name
- **WHEN** a run asks for a goal that no pack ever exported
- **THEN** it fails with the existing goal-not-found error, not a lifecycle block

### Requirement: The exclusion is reported
Ingesting a source SHALL name every pack it excluded as retired, rather than deploying a smaller plan in
silence.

#### Scenario: Packs are excluded
- **WHEN** ingest excludes one or more packs as retired
- **THEN** the result names each excluded pack

#### Scenario: Nothing is excluded
- **WHEN** no pack in the source is retired
- **THEN** the run behaves exactly as it does today

### Requirement: A pack with no recorded state is runnable
The runtime SHALL treat a missing or unreadable lifecycle sidecar as no state recorded, and SHALL run the
pack, matching the fallback the improvement loop already applies.

#### Scenario: Pack published before stamping existed
- **WHEN** a pack directory has no lifecycle sidecar
- **THEN** the pack is ingested and runs

#### Scenario: Corrupt sidecar
- **WHEN** a pack's lifecycle sidecar cannot be read
- **THEN** the pack is ingested and runs, and it is not treated as retired

### Requirement: Only retirement blocks a run
The runtime SHALL gate on the single terminal state and SHALL NOT block a pack for being incubating or
degraded, and SHALL offer no option to run a retired pack.

#### Scenario: A degraded pack is run
- **WHEN** a pack's sidecar declares it degraded
- **THEN** the pack is ingested and runs

#### Scenario: Someone asks to run a retired pack
- **WHEN** a caller wants a retired pack to run
- **THEN** there is no runtime option to allow it, and the documented revival is an explicit rebuild that
  republishes the pack

### Requirement: Reading the state does not couple the runtime to the writer
The runtime SHALL obtain the declared state without depending on the module that writes the sidecar, so
that the reader stays independent of the generation and publishing stack.

#### Scenario: A new runtime build
- **WHEN** the runtime module is built
- **THEN** it resolves no dependency on the builder module to read a pack's lifecycle state

#### Scenario: A second reader needs the state later
- **WHEN** another module needs the same state
- **THEN** the shared lifecycle types are relocated to the shared module at that point, rather than a
  second parser being added
