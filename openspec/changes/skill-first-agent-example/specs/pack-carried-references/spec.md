# Spec Delta

## Purpose

This widens what a step's `references:` may name. Skills are one kind of Embabel reference; the platform
ships several, and a pack that can carry any of them can hand a step a directory, a document or a short
brief the way it hands it a skill. This delta also closes the silent-failure paths that make a reference
channel unsafe to write against.

## ADDED Requirements

### Requirement: A step's references are not limited to skills

A step SHALL be able to name references that a pack carries, and a reference SHALL be one of the kinds the
runtime can construct from files in the pack — a skill folder, a directory, a document, a short literal
brief. Each carries a name the step uses, and the run reports which kind answered.

#### Scenario: A step names a directory reference

- **GIVEN** a pack carrying a reference that resolves to a directory
- **WHEN** the step that names it executes
- **THEN** the model is given that reference's description, notes and prefixed tools

#### Scenario: A step names several references

- **WHEN** a step names a skill and a document
- **THEN** both are present on the call, each under its own prefix

#### Scenario: A kind the runtime cannot construct

- **WHEN** a pack declares a reference kind the runtime has no recipe for
- **THEN** validation refuses it and names the kind, rather than dropping it at run time

### Requirement: Reference names are unique once sanitized

Reference names in one pack SHALL be unique after the platform's sanitization, because two names that
collapse to the same tool prefix collide silently upstream. A collision SHALL be refused before deploy,
naming both files.

#### Scenario: Two names that sanitize alike

- **GIVEN** a pack with two references whose names differ only in characters the prefix rewrites
- **WHEN** the pack is validated
- **THEN** validation refuses the pair and names both, rather than one reference's tools being overwritten

#### Scenario: A name that cannot become a prefix

- **WHEN** a declared name sanitizes to something unusable as a tool prefix
- **THEN** validation refuses it

### Requirement: A declared reference that resolves to nothing is a validation error

A step that names a reference the pack does not carry SHALL be refused before deploy, naming the step and
the name. The upstream reader drops an unmatched name quietly, so the check cannot be left to run time.

#### Scenario: A misspelled name

- **WHEN** a step names a reference the pack does not carry
- **THEN** validation fails on that step alone, and the pack is not deployable until it is fixed

#### Scenario: A reference whose backing path is gone

- **WHEN** a declared reference points at a file or folder that does not exist in the pack
- **THEN** validation refuses it, naming the path

#### Scenario: A pack that declares none

- **WHEN** no step names a reference
- **THEN** validation passes and the run offers none, which is the unchanged case

### Requirement: A reference is offered without burying the step

A reference's tools SHALL be offered under its own prefix, and where a reference would place a large tool
surface in front of a model that also has other work to do, the runtime SHALL be able to attach it behind a
single facade tool whose inner tools appear only once it is used. The run report SHALL show what was
attached and in which form.

#### Scenario: Attached behind a facade

- **GIVEN** a reference attached in facade form
- **WHEN** a step's tools are listed
- **THEN** the facade is listed and the inner tools are not, until the facade is used

#### Scenario: Attached directly

- **GIVEN** the same reference attached directly
- **WHEN** a step's tools are listed
- **THEN** its tools are listed under their prefixed names from the start

#### Scenario: The form is stated

- **WHEN** a run reports the references attached to a step
- **THEN** it says which form each was attached in

### Requirement: An unknown step key is reported, not swallowed

The format's reader ignores a key it does not know, which makes a mistyped or invented key read as honored
while doing nothing. Validation SHALL report a step key that is not part of the declared set, so a pack
cannot silently carry a wish the runtime never implements.

#### Scenario: A mistyped key

- **WHEN** a step file contains a key that differs from a real one by spelling
- **THEN** validation reports it as unrecognized, naming the step and the key

#### Scenario: An invented control key

- **GIVEN** a step file that names a capability the format does not implement, such as a per-step approval
  or retry count
- **WHEN** the pack is validated
- **THEN** validation says the key is not implemented rather than accepting it, because a silently ignored
  safety key is worse than an absent one

#### Scenario: A key that survives staging but not a rewrite

- **GIVEN** a key that reaches the upstream reader through the runtime's staging copy but is not part of
  the model the writer emits
- **WHEN** the pack is validated
- **THEN** validation names it as not preserved across a rewrite, so a pack cannot depend on it surviving a
  studio save or a builder repair
