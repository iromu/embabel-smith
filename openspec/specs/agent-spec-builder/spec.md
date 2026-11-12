# agent-spec-builder Specification

## Purpose
The standalone builder: turns a natural-language description into a validated, security-scanned, evaluated agent spec and publishes it into a folder or git source. It is the production counterpart of POCs 2/3/5/9/18/21, assembled into one pipeline with a publish target.

## Requirements

### Requirement: Build a validated pack from a description
Given a natural-language description, the builder SHALL produce a draft agent spec (action steps, exported goal, intermediate types), validate it against a throwaway platform, and write it as an agent spec — never writing an invalid draft (the D5 invariant from the POC phase).

#### Scenario: Coherent description builds
- **WHEN** the builder is given a coherent description
- **THEN** it produces a written agent spec that passes structure validation and platform binding

#### Scenario: Invalid draft is not written
- **WHEN** a draft fails binding or schema validation
- **THEN** the builder reports the specific issues and writes nothing to the output source

#### Scenario: Ambiguous description
- **WHEN** the description cannot be resolved cleanly
- **THEN** the builder returns the draft together with the specific issues needing review, rather than silently guessing

### Requirement: Evaluate and refine with a bound
The builder SHALL evaluate the built pack and, while tasks fail and the refinement bound has not been reached, SHALL rewrite prompts from the failures, re-validate, and re-evaluate.

#### Scenario: Eval passes on first attempt
- **WHEN** the evaluation meets its acceptance criteria
- **THEN** the current prompts are retained and the build is reported with its passing score

#### Scenario: Refinement improves a failing pack
- **WHEN** an evaluation task fails and the bound is not reached
- **THEN** the builder produces a revised prompt from the failure, re-validates, and re-evaluates

#### Scenario: Bound reached
- **WHEN** the maximum refinement count is exhausted with failures remaining
- **THEN** the builder stops, reports the final score and the remaining failures, and does not publish below the gate

### Requirement: Security scan gates publication
The builder SHALL run the install-time security scan (POC 21) on the built pack before publishing, and SHALL NOT publish a pack with high-severity findings.

#### Scenario: Clean pack publishes
- **WHEN** the scan reports no high-severity findings
- **THEN** the pack is eligible for publication

#### Scenario: High-severity finding blocks
- **WHEN** the scan reports a high-severity finding
- **THEN** publication is blocked and the finding is reported; the pack is not written to the output source

### Requirement: Pack carries lifecycle metadata
The published pack SHALL carry its content hash, its baseline eval pass-rate, and an initial lifecycle state of `incubating` (POC 18 contract), so the runtime and the self-improvement loop can detect drift and regressions.

#### Scenario: Metadata present
- **WHEN** a pack is published
- **THEN** its manifest (or lifecycle sidecar) records content hash, baseline pass-rate, and state `incubating`

### Requirement: Publish into a folder or git source
The builder SHALL publish the finished pack into an output source addressed by the same URI grammar as change 02: `folder:<path>` writes the pack directory; `git:<path-or-url>` commits the pack into the repository's working tree.

#### Scenario: Folder publish
- **WHEN** the output is `folder:./built-specs`
- **THEN** the pack appears as `./built-specs/<pack-name>/` with all format files

#### Scenario: Git publish commits locally
- **WHEN** the output is a git source
- **THEN** the pack is added to the repository's working tree and committed with a message naming the pack and its baseline score

#### Scenario: Git publish does not push by default
- **WHEN** the builder publishes to a git source
- **THEN** it never pushes to a remote unless explicitly told to (an explicit `--push` flag); the default is a local commit only

#### Scenario: Name collision in target
- **WHEN** the target already contains a pack with the same name
- **THEN** the builder reports the collision and does not silently overwrite; an explicit `--overwrite` flag replaces it

### Requirement: CLI contract
The builder SHALL provide a CLI: `smith build "<description>" --out <source-uri> [--max-refine <n>] [--overwrite] [--push]`, printing the build report (pack name, validation, scan, score, publish location) on success and a clear error on failure.

#### Scenario: CLI build
- **WHEN** the CLI is invoked with a description and a valid output source
- **THEN** it prints the build report and exits zero

#### Scenario: CLI blocked build
- **WHEN** validation, the security gate, or the score gate blocks the build
- **THEN** it prints the blocking reason and exits non-zero, having written nothing to the output source

### Requirement: Offline-testable pipeline control
The pipeline's control logic (validation gating, scan gating, refinement bound, hashing, publishing, collision handling) SHALL be unit-testable without a model endpoint; only the compile and eval steps invoke the model.

#### Scenario: Offline tests pass
- **WHEN** `mvn test` runs on `prod/embabel-smith-builder`
- **THEN** all offline tests pass with no model endpoint configured

### Requirement: Builder runs as a single agent
The builder's build component SHALL be a single Embabel agent whose goal is that a validated
agent spec exists on disk, reached by a deterministic pipeline of code-backed actions
(compile, validate, write); the model is invoked only inside the compile action.

#### Scenario: Build is one agent's plan
- **WHEN** the builder is given a coherent description
- **THEN** the compile → validate → write steps execute as actions of that single agent, whose
  goal is the written agent spec

#### Scenario: Model-gated compile, pure-Java tail
- **WHEN** the build pipeline runs
- **THEN** only the compile action calls the model, and validate and write execute as pure
  Java in deterministic order
