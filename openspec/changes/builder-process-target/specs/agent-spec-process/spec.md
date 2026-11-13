# Spec Delta

## Purpose

The builder decomposes a business-process description into a multi-agent process alongside the
single-agent spec, validates the cross-agent hand-offs, recommends which to publish, and evaluates
the composed process.

## ADDED Requirements

### Requirement: Generate both a single-agent spec and a multi-agent process
Given a business-process description, the build SHALL generate two candidates: a single-agent spec
and a multi-agent process — a named, ordered list of agent specs in which the first agent takes
`UserInput`, the last agent's goal is the process's final deliverable, and agents connect by sharing
intermediate types.

#### Scenario: Both candidates are generated
- **WHEN** a build runs
- **THEN** it produces both a single-agent spec and a multi-agent process

#### Scenario: Process decomposes into agents
- **WHEN** the description is coherent
- **THEN** the process candidate is two or more agent specs that share intermediate types

### Requirement: Validate the process's cross-agent hand-offs
The builder SHALL validate a `ProcessSpec` for cross-agent hand-offs — every step input is produced
by some agent or is `UserInput`, each agent's goal output is reachable from the process entry, goal
names are unique, and the process flows from `UserInput` to the final goal — and SHALL detect
circular dependencies between agents.

#### Scenario: Well-formed process validates
- **WHEN** a `ProcessSpec` satisfies all hand-off checks
- **THEN** validation reports it valid

#### Scenario: Circular dependency is detected
- **WHEN** two agents depend on each other's outputs
- **THEN** validation reports the cycle and the process is not valid

### Requirement: Repair a detected cycle
When validation detects a cycle, the builder SHALL attempt a bounded repair (reorder the agents)
and re-validate; when the repair cannot remove the cycle, the build SHALL block.

#### Scenario: Repair removes the cycle
- **WHEN** a cycle can be removed by reordering
- **THEN** the repaired `ProcessSpec` re-validates and the build proceeds

#### Scenario: Repair fails
- **WHEN** the cycle cannot be removed within the bound
- **THEN** the build blocks at validation and reports the cycle

### Requirement: Evaluate both candidates
The builder SHALL evaluate the single-agent spec and the composed process (running the process's
entry goal across all of its packs), scoring each against the eval tasks.

#### Scenario: Both candidates are scored
- **WHEN** the build evaluates its candidates
- **THEN** the single-agent spec and the composed process are each scored

### Requirement: Recommend the better candidate
The build SHALL recommend the candidate that validates cleanly and has the higher eval score; when
the multi-agent process is not valid, or the scores tie, it SHALL recommend the single-agent spec.

#### Scenario: Valid, higher-scoring process recommended
- **WHEN** the process is valid and out-scores the single-agent spec
- **THEN** the build recommends the process

#### Scenario: Invalid process falls back to single
- **WHEN** the process fails validation after repair
- **THEN** the build recommends the single-agent spec

#### Scenario: Tie recommends single
- **WHEN** both candidates are valid with equal scores
- **THEN** the build recommends the single-agent spec

### Requirement: Store both, publish the recommended
The build SHALL store both candidates and publish only the recommended one into an output source
addressed by the same URI grammar as the spec builder (`folder:` or `git:`), with the same collision
and no-push-by-default semantics, so the runtime can ingest and compose the published packs.

#### Scenario: Recommended candidate publishes
- **WHEN** the build completes
- **THEN** the recommended candidate is published to the target and both candidates are stored
