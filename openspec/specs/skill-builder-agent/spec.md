# skill-builder-agent Specification

## Purpose

The Embabel meta-agent that turns a natural-language description into a validated, on-disk skill-pack. It is itself an agent built with Embabel, and its output — a skill-pack — is exactly the unit the no-code runtime ingests, so the builder generates runtimes with no code.

## Requirements

### Requirement: Compile a description into a draft skill
Given a natural-language description, the builder SHALL produce a draft skill consisting of one or more action steps, an exported goal, and any intermediate types needed to connect them.

#### Scenario: Description produces a draft
- **WHEN** the builder is given a coherent description of a desired skill
- **THEN** it produces a draft with at least one action step, an exported goal, and any intermediate types the chain requires

#### Scenario: Ambiguous description
- **WHEN** the builder is given a description that cannot be resolved cleanly
- **THEN** it produces a draft together with the specific issues that need review, rather than silently guessing

### Requirement: Validate before writing
The builder SHALL validate a draft against a throwaway platform (binding and schema checks) and MUST NOT write an invalid draft to a skill-pack.

#### Scenario: Valid draft passes
- **WHEN** a draft's bindings and schemas are consistent
- **THEN** validation passes and the draft is eligible to be written

#### Scenario: Invalid draft is not written
- **WHEN** a draft fails binding or schema validation
- **THEN** the builder reports the failure and writes nothing to the skill-pack

### Requirement: Write the skill-pack
On a valid draft, the builder SHALL write the skill-pack to disk in the skill-pack format (manifest, action steps, exported goal, and intermediate types).

#### Scenario: Writes well-formed pack
- **WHEN** the builder receives a validated draft
- **THEN** it writes a directory conforming to the skill-pack format that the no-code runtime can ingest

### Requirement: Score and rewrite prompts from failures
The builder SHALL run an evaluation against the generated skill, and when a task fails, SHALL use the failure together with the current prompt to produce a revised prompt, then re-validate.

#### Scenario: Evaluation passes
- **WHEN** the evaluation of the generated skill meets its acceptance criteria
- **THEN** the builder reports a passing score and the current prompt is retained

#### Scenario: Evaluation fails and prompt is improved
- **WHEN** an evaluation task fails
- **THEN** the builder produces a revised prompt from the failure and re-validates the revised draft

### Requirement: Builder runs as a single agent
The builder SHALL run as a single agent whose goal is that a validated skill-pack exists on disk, planning its own steps (compile, validate, write, evaluate) over typed preconditions rather than through hardcoded orchestration.

#### Scenario: Builder plans its pipeline
- **WHEN** the builder is given a description
- **THEN** it plans the compile → validate → write → evaluate sequence by matching typed preconditions, and the pipeline runs to a written skill-pack
