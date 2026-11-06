# skill-pack-format Specification

## Purpose

Defines the on-disk YAML contract for a deployable skill-pack — the single unit of capability that the builder emits and the no-code runtime ingests. It fixes the structure both producer and consumer must honor, so a skill-pack is portable and inspectable without any compiled code.

## Requirements

### Requirement: Skill-pack directory layout
A skill-pack SHALL be a directory containing a `manifest.yml`, a `steps/` directory of action-step files, a `goal.yml`, and a `types/` directory of intermediate-type definitions.

#### Scenario: Well-formed pack
- **WHEN** a directory contains `manifest.yml`, `goal.yml`, at least one file under `steps/`, and a `types/` directory
- **THEN** it is recognized as a valid skill-pack structure

#### Scenario: Missing goal
- **WHEN** a candidate directory has `manifest.yml` and `steps/` but no `goal.yml`
- **THEN** it is rejected as an incomplete skill-pack

### Requirement: The skill is an exported goal
The skill-pack SHALL define exactly one exported goal in `goal.yml`, declared with `export: true` and a named output type. This exported goal is the callable skill unit.

#### Scenario: Goal is exported
- **WHEN** `goal.yml` declares `stepType: goal`, a name, an `outputTypeName`, and `export: true`
- **THEN** the skill-pack exposes exactly one callable skill with that output type

#### Scenario: Goal not exported
- **WHEN** `goal.yml` declares `export: false` (or omits it)
- **THEN** the skill-pack is invalid, because no callable skill is exposed

### Requirement: Action steps are prompt-backed
Each file under `steps/` SHALL declare `stepType: action`, a name, a description, a list of input type names, a single output type name, and a prompt template that references its inputs by name.

#### Scenario: Valid action step
- **WHEN** a step file declares an action whose prompt references only names bound by its declared input types
- **THEN** the step is accepted as a prompt-backed action

#### Scenario: Action references undeclared input
- **WHEN** a step's prompt references a variable that is not bound by any declared input type
- **THEN** the step is reported as invalid

### Requirement: Intermediate types are pure-data
Each file under `types/` SHALL declare a named type with a description and a set of named, typed own-properties, and SHALL require no compiled class to exist.

#### Scenario: Valid dynamic type
- **WHEN** a type file declares a name, a description, and one or more own-properties each with a name and a type
- **THEN** the type is accepted as a pure-data definition

### Requirement: Type references resolve within the pack
Every input and output type name referenced by the goal or by any action step SHALL resolve to either a framework-provided type or a type declared in `types/`.

#### Scenario: All references resolve
- **WHEN** every type name used by the goal and action steps is either framework-provided or declared in `types/`
- **THEN** the skill-pack passes type-resolution validation

#### Scenario: Dangling type reference
- **WHEN** an action step's `outputTypeName` is neither framework-provided nor declared in `types/`
- **THEN** validation fails and names the unresolved type

### Requirement: Manifest declares runtime defaults
`manifest.yml` SHALL declare the skill-pack name and version, and MAY declare a default planner and a default model.

#### Scenario: Minimal manifest
- **WHEN** `manifest.yml` declares at least a name and a version
- **THEN** the skill-pack manifest is valid
