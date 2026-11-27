# Spec Delta

## Purpose

Defines what the no-code runtime does with a script carried by a skill: whether it can run at all, which
engine runs it, where it is confined, what a run reports about it, and how a script tool reaches the model
without every script in the folder reaching every step.

## ADDED Requirements

### Requirement: Skill scripts are executed only when switched on

The runtime SHALL be able to execute scripts carried by a skill, and that capability SHALL be off by
default, switched on by configuration in the same shape as the other optional features of the deployment.
With the switch off, a skill that carries scripts is still readable and its instructions and resources are
still offered; only execution is withheld, and the run says so rather than staying quiet.

#### Scenario: Off, which is the default

- **GIVEN** a run with no script property set
- **WHEN** a step names a skill whose folder contains a `scripts/` directory
- **THEN** no script tool is offered, and the run states that the skill carries scripts and that this
  reader is not executing them

#### Scenario: Switched on

- **WHEN** the script switch is enabled and a step names that skill within a tier that allows it
- **THEN** the skill's scripts are callable and a call reaches an engine

#### Scenario: A pack that carries nothing

- **WHEN** a skill folder has no `scripts/` directory
- **THEN** the run reports no scripts and does not claim a withheld capability

### Requirement: The engine is named, and what it refuses is named

A run SHALL execute skill scripts through one of the engines the platform ships — a host-process engine, a
container engine, or none — selected by configuration rather than by a class named in a property. Where a
script's language is outside what the selected engine supports, the runtime SHALL say so by script and
language instead of offering a tool that cannot run.

#### Scenario: A language the engine does not take

- **GIVEN** an engine that supports some languages and not others
- **WHEN** a skill carries a script in a language outside that set
- **THEN** no tool is offered for that script and the run names the script and its language

#### Scenario: A container engine selected where none is available

- **WHEN** the configuration selects a container engine and the host cannot run containers
- **THEN** start-up refuses with the reason, rather than falling back silently to running on the host

#### Scenario: Nothing selected

- **WHEN** scripts are switched on with no engine named
- **THEN** the documented default engine is used and the report says which one that was

### Requirement: A script runs inside the same confinement as a file tool

Script execution SHALL be confined to the configured run root for its input files, SHALL carry an explicit
time limit, and SHALL NOT inherit the process environment beyond what the engine's own allowlist permits.
A script that escapes the root, outlives its limit, or reads more than it was given is stopped, and the
stop is reported as the reason rather than as a generic failure.

#### Scenario: A script asks for a file outside the root

- **WHEN** a script is handed an input path outside the run root
- **THEN** the call is refused, naming the path and the root

#### Scenario: A script that never returns

- **WHEN** a script runs past its time limit
- **THEN** it is stopped, the run records the timeout as the outcome, and later steps still execute

#### Scenario: The environment a script sees

- **WHEN** a script runs on a host that has unrelated credentials in its environment
- **THEN** only the permitted entries reach it, and the report states the engine's environment policy in
  one line

### Requirement: A script's outcome is data, not an exception

A script call SHALL return its exit status, its captured output with any truncation stated, and any
produced artifacts to the step that called it. A non-zero exit is an answer the model can act on, not a
run failure; a run fails only when the engine itself could not be started.

#### Scenario: A check that fails

- **WHEN** a script exits non-zero with a message on standard error
- **THEN** the step receives the status and the message and the run continues

#### Scenario: A script that prints more than the cap

- **WHEN** output exceeds the configured limit
- **THEN** it is truncated and the truncation is stated in what the model receives

### Requirement: Scripts reach the steps that were meant to have them

A script tool SHALL NOT be offered to a step merely because a skill carrying it happens to be loaded.
Where attaching a skill would place every script of that skill in front of a model that was not given the
skill, the runtime SHALL offer the narrower, script-free surface to that step instead, and the run report
SHALL show, per step, whether scripts were in play.

#### Scenario: A reading step and an executing step

- **GIVEN** one pack with a step that reads the skill's instructions and a different step whose job is to
  run its scripts
- **WHEN** the run offers tools to each step
- **THEN** the reading step sees no script tool and the executing step does, and the report distinguishes
  them

#### Scenario: Scripts on, step names no skill

- **WHEN** scripts are switched on and a step declares no reference
- **THEN** no script tool is offered to that step

### Requirement: Which scripts ran is part of the run's record

The run SHALL record, per script call, the skill it came from, the script name as it appeared on the wire,
the engine that ran it, the outcome, and the elapsed time. A claim that a skill's script ran must be
answerable from the record rather than from a log a reader has to grep.

#### Scenario: One script called once

- **WHEN** a script is called and returns
- **THEN** the record shows the skill, the callable name, the engine, the exit status and the duration

#### Scenario: A script named on the wire that differs from the folder

- **GIVEN** a script whose callable name is formed from a file name containing characters the wire form
  rewrites
- **WHEN** the run reports the call
- **THEN** it shows both the file it came from and the name the model used
