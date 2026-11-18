# Spec Delta

## Purpose

Runs the builder against a configured real model on declared examples, on request rather than by
default, and leaves behind artifacts a person can inspect to judge what the build produced and how
well it scored.

## ADDED Requirements

### Requirement: Live model tests are excluded from the default test run
The build SHALL NOT run tests that require a live model during a default test run, and SHALL run them
on request without any change to the tests themselves.

#### Scenario: Default run without an endpoint
- **WHEN** the test phase runs with no live group requested and no model endpoint configured
- **THEN** no live test executes and the run succeeds

#### Scenario: Live run on request
- **WHEN** the live group is requested
- **THEN** every test marked as requiring a live model is selected for execution

### Requirement: One documented entry point runs the live group
The project SHALL provide one documented command that supplies the model-endpoint environment and runs
the live group, so that opting in does not depend on a developer knowing which variables to export.

#### Scenario: Live entry point invoked
- **WHEN** a developer runs the documented live entry point
- **THEN** the endpoint environment is in effect for that run and the live group executes

#### Scenario: No endpoint configured
- **WHEN** the live group is requested on a machine with no model endpoint configured
- **THEN** the live tests are reported as skipped rather than failed

### Requirement: Example verification is launched by hand, one blueprint at a time
Building an example against a real model SHALL be done by a script under the examples tree that
handles one blueprint and one output target per run, and SHALL NOT be wired into a test phase.

#### Scenario: A person wants an example built
- **WHEN** someone runs an example script naming a blueprint
- **THEN** the script builds that blueprint's documents through the product CLI and leaves the build
  report where the script said it would

#### Scenario: A run stops at a gate
- **WHEN** the build stops at a gate rather than publishing
- **THEN** the script still leaves the report naming the stage and the reason, and exits non-zero

#### Scenario: Someone asks for a test phase to build the examples
- **WHEN** a change proposes running the example matrix from a test phase
- **THEN** it is refused: the matrix was run once, cost roughly two hundred model calls over fifty
  minutes of endpoint time, and published nothing

### Requirement: A blueprint is a directory, not a declaration
The set of example blueprints SHALL be the directories under the examples blueprints tree, so that
adding an example means adding documents and editing no script, test or manifest.

#### Scenario: A blueprint is added
- **WHEN** a new directory of business-process documents is placed under the blueprints tree
- **THEN** an example script can build it by name without any file being edited

#### Scenario: A name matches no blueprint
- **WHEN** a script is given a name that matches no blueprint directory
- **THEN** it exits with a usage error listing the blueprints that do exist

### Requirement: A script states what it will cost
Each example script SHALL state in its own header what one run costs in model calls and what makes
that cost grow, before the person running it spends anything.

#### Scenario: A costly script is read before it is run
- **WHEN** someone opens the skill script
- **THEN** its header names the per-run call count and the stage that multiplies it, so a batch can be
  sized deliberately

### Requirement: Blueprints are built from as written
An example run SHALL pass a blueprint's documents to the build directly. The codebase-discovery prompt
SHALL NOT be run over them, because that prompt reconstructs a business process from a codebase and a
blueprint is already that business process.

#### Scenario: A blueprint is built
- **WHEN** an example script builds a blueprint
- **THEN** the build receives the documents themselves and no extraction call is made

#### Scenario: Someone adds the extraction flag to an example script
- **WHEN** a change adds extraction back in front of a blueprint
- **THEN** it is refused, and the prompt's codebase premise is named as the reason
