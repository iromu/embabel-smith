# Spec Delta

## Purpose

Select a single skill from a same-typed candidate set using a small local LLM served in a
Docker container whose lifecycle the application manages, and measure its selection accuracy
against the lexical, dense, and transformer baselines on the skill-shadowing scenario — under
both the model's in-distribution decision prompt format and an out-of-distribution free-form
format.

## ADDED Requirements

### Requirement: Select one skill from a candidate set with a small LLM

The LLM selector SHALL select the single most relevant skill from a provided candidate set for
a given task query by asking the served model to choose among the candidates, and SHALL parse
the model's reply into exactly one selection from the candidate set.

#### Scenario: Selects the candidate the model names
- **WHEN** a task query is asked of the model against a candidate set containing the canonical skill and related near-duplicate skills
- **THEN** the selector returns exactly one selected skill — the candidate the model's reply refers to

#### Scenario: Reply does not name a candidate
- **WHEN** the model's reply does not refer to any candidate in the set
- **THEN** the selector reports that target as a miss rather than guessing a selection

#### Scenario: Empty candidate set
- **WHEN** a task query is classified against an empty candidate set
- **THEN** the selector returns no selection

### Requirement: Evaluate the model under two prompt formats

The benchmark SHALL evaluate the LLM selector under two prompt formats on the same candidate
sets: the model's in-distribution typed-decision format (the question with lettered options and
an instruction to answer with the single letter of the correct option) and an
out-of-distribution free-form format that names the candidates without the lettered-option
contract.

#### Scenario: In-distribution format
- **WHEN** the in-distribution format is used for a target
- **THEN** the prompt presents the candidates as lettered options and the selector parses a single letter from the reply

#### Scenario: Out-of-distribution format
- **WHEN** the out-of-distribution format is used for the same target
- **THEN** the prompt presents the candidates without the lettered-option contract and the selector parses the referenced candidate from the reply

#### Scenario: Both formats scored on identical candidates
- **WHEN** both formats are evaluated for a given target
- **THEN** each is scored against the identical candidate set for that target, so the ID/OOD gap is directly measurable

### Requirement: Serve the model in a Docker container managed by the application

The application SHALL serve the model through a Docker container on a fixed local port, SHALL
start the container when the application starts and wait until the model endpoint is ready
before running the benchmark, and SHALL stop the container when the application shuts down.
If a container for the model is already running, the application SHALL connect to it rather
than starting a second one.

#### Scenario: Container started with the application
- **WHEN** the application starts and no model container is running
- **THEN** the container is started, the benchmark runs only after the model endpoint reports ready, and the container is stopped when the application shuts down

#### Scenario: Container already running
- **WHEN** the application starts and the model container is already running and ready
- **THEN** the application connects to the existing container without restarting it

### Requirement: Report selection accuracy across methods on identical candidate sets

The benchmark SHALL evaluate the small-LLM selector (under both prompt formats), the
transformer baseline, the lexical baseline, and the dense baseline on the same candidate sets,
and report Precision@1 for each method across the benchmark targets so their results are
directly comparable.

#### Scenario: One Precision@1 per method and format
- **WHEN** the benchmark runs over its six targets
- **THEN** it reports a Precision@1 value for each of the four methods, with the small-LLM method reported separately under each of the two prompt formats

#### Scenario: Methods scored on identical candidates
- **WHEN** the methods are evaluated for a given target
- **THEN** each is scored against the identical candidate set for that target

### Requirement: Run as a docker-gated benchmark

The benchmark SHALL run end-to-end only when the Docker daemon is reachable and the model
weights are available locally, SHALL report a clear, actionable error when either is absent,
and SHALL allow its non-model logic to be verified offline without Docker.

#### Scenario: Docker and weights present
- **WHEN** the benchmark is launched with Docker available and the weights present
- **THEN** it runs the full comparison and reports the results

#### Scenario: Weights absent
- **WHEN** the benchmark is launched without the model weights
- **THEN** it reports a clear error identifying that the weights are missing and the exact one-time command to obtain them, rather than an opaque failure

#### Scenario: Docker unavailable
- **WHEN** the benchmark is launched and the Docker daemon is not reachable
- **THEN** it reports a clear error identifying that Docker is required, rather than an opaque failure

#### Scenario: Offline verification without Docker
- **WHEN** the offline test suite runs without Docker
- **THEN** the selection, prompt-building, reply-parsing, and benchmark logic are verified with the model endpoint stubbed, and the build stays green
