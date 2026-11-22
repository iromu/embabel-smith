# offline-verification-pipeline Specification

## Purpose
Verifies a change automatically without depending on a model: the commands that run, the condition
they run in, and what a green result does and does not prove about the repository.

## Requirements

### Requirement: A change is verified without anyone remembering to run it

The project SHALL verify each change automatically on a pull request and on a push to the default
branch, by compiling the reactor, running the offline test suite and running the project's style
checks.

#### Scenario: A change does not compile

- **WHEN** a proposed change breaks compilation in any module of the default reactor
- **THEN** the run fails and names the module

#### Scenario: A style rule is violated

- **WHEN** a proposed change introduces a violation of the project's configured style checks
- **THEN** the run fails even though the code compiles and the tests pass

#### Scenario: Nobody runs a build locally

- **WHEN** a change is proposed by someone who runs no build at all
- **THEN** it is still compiled, tested and style-checked before it can merge

### Requirement: The verification run has no model endpoint

The verification run SHALL NOT be given a model endpoint or credentials for one, and SHALL NOT select
the tests that require a live model, so that a green result cannot depend on what a model answers.

#### Scenario: A live test would fail on model output

- **WHEN** a test that needs a live model would fail because of what the model returned
- **THEN** the verification run is unaffected, because that test is not part of it

#### Scenario: Someone proposes adding an endpoint to the run

- **WHEN** a change proposes giving the verification run model credentials
- **THEN** it is refused: the run's value is that it is deterministic and costs nothing per commit

### Requirement: The run uses the project's documented entry points

The verification run SHALL invoke the repository's documented build entry points rather than
reimplementing their command lines, and SHALL use the pinned build wrapper.

#### Scenario: A developer reproduces a failure

- **WHEN** someone wants to reproduce a failing check locally
- **THEN** the command they run is the same command the run executed

#### Scenario: The build tooling version matters

- **WHEN** the run needs the build tool
- **THEN** it uses the repository's pinned wrapper rather than whatever the runner happens to provide

### Requirement: What the run proves is stated where green is read

The project SHALL state, in its run book and its architecture reference, which parts of the repository
the verification run covers and which it deliberately does not.

#### Scenario: A reader sees a green run

- **WHEN** someone reads a successful verification result
- **THEN** they can find, without asking, that the model-driven paths and the frozen proof-of-concept
  reactor are outside what it checked

#### Scenario: A module is left out of the run

- **WHEN** the run covers less than the whole repository
- **THEN** the exclusion is stated where its green is read, rather than left for a reader to infer
