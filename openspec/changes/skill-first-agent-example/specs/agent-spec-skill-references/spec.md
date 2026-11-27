# Spec Delta

## Purpose

Defines how an agent spec carries Agent Skills inside its own directory and how a step names the skills
it needs, so a pack states its whole capability in one portable folder of pure data.

## ADDED Requirements

### Requirement: A pack may carry skills

An agent spec directory SHALL be valid whether or not it carries a `skills/` directory, and where the
directory exists each entry is a skill folder with a `SKILL.md` whose front matter declares a `name` and
a `description`. A pack without the directory MUST behave exactly as it does today. Skill folders are
data, never compiled code, and the pack remains ingestible with zero compilation.

#### Scenario: A pack with a skill folder validates

- **GIVEN** a pack carrying `manifest.yml`, an exported `goal.yml`, a non-empty `steps/`, `types/`, and
  `skills/<name>/SKILL.md`
- **WHEN** the pack is validated
- **THEN** validation passes and the skill is listed as carried by the pack

#### Scenario: A pack without a skills folder is unchanged

- **GIVEN** a pack that carries no `skills/` directory
- **WHEN** the pack is validated and ingested
- **THEN** validation passes and the pack runs exactly as before this change

#### Scenario: A skill folder without a skill definition is not a skill

- **GIVEN** a pack whose `skills/` directory holds a folder with no `SKILL.md`
- **WHEN** the pack is validated
- **THEN** the folder is reported by name as a skill the pack does not actually carry

### Requirement: A step names the references it needs

An action step SHALL declare the skills it needs under `references:`, each entry the declared name of a
skill the pack carries. The key is the framework's own step-spec key, so the declaration is a request for
that capability, not a grant of reach: a step declaring none MUST be offered none, and the declaration
adds no trust claim of its own and is not a tool group.

#### Scenario: A step references a skill the pack carries

- **GIVEN** a pack carrying a skill named `repo-notes`
- **WHEN** one of its steps declares `references: [repo-notes]`
- **THEN** the step validates and the pack's declared reference set contains `repo-notes`

#### Scenario: A step declares no references

- **WHEN** a step omits `references:`
- **THEN** the step is offered nothing from the pack's skills, whatever the pack carries

#### Scenario: A goal declares references

- **WHEN** a `goal.yml` declares `references:`
- **THEN** the declaration is ignored, because a goal contributes no model call of its own, and the
  pack still validates

### Requirement: A dangling reference is refused before deploy

A declared reference that names no skill the pack carries SHALL be refused by validation, naming both
the step and the reference. The check is the reference counterpart of the existing rule that every type
reference resolves, and it exists because a name that resolves to nothing is offered to no model and
the run answers as though the capability never existed.

#### Scenario: The named skill is absent

- **GIVEN** a step declaring `references: [repo-notes]` in a pack carrying no such skill
- **WHEN** the pack is validated
- **THEN** validation fails with a dangling-reference issue that names the step and `repo-notes`

#### Scenario: The named skill is present but misspelled in one step only

- **GIVEN** a pack carrying `repo-notes` where one step spells it `repo_notes`
- **WHEN** the pack is validated
- **THEN** validation fails naming the spelling step, and the correctly-spelled step is not reported

### Requirement: Agents in one folder may share skills

Several agent definitions SHALL each remain a valid pack when placed side by side in one folder, and a
`skills/` directory beside those definitions SHALL be shared by every agent in that folder. An agent's own
`skills/` directory MUST take precedence when both carry the same name. The shared set is the immediately
enclosing folder only, never a chain of ancestors.

#### Scenario: Two agents reference one shared skill

- **GIVEN** a folder holding two agent definitions and a `skills/` directory with a skill named
  `repo-notes`
- **WHEN** either agent's step declares `references: [repo-notes]`
- **THEN** the reference resolves to the shared folder and the pack validates

#### Scenario: An agent shadows a shared name

- **GIVEN** a shared `repo-notes` and an agent carrying its own `repo-notes`
- **WHEN** that agent's step references it
- **THEN** the agent's own copy serves the reference and the report says which folder answered

#### Scenario: An agent carries nothing of its own

- **GIVEN** an agent with no `skills/` directory of its own inside a folder that has one
- **WHEN** its step references a skill the shared folder carries
- **THEN** the reference resolves

#### Scenario: The shared set stops at the enclosing folder

- **GIVEN** an agent whose skills are named from a directory two levels above it
- **WHEN** the pack is validated
- **THEN** the reference is dangling, because only the immediately enclosing folder is shared

### Requirement: The format round-trips references

A pack written from the data model SHALL serialize a step's references and the reader SHALL read them
back, so that read-then-write leaves the declaration intact. An unknown key in a step file is dropped
silently by the reader, so a capability the model cannot carry is a capability the first rewrite loses.

#### Scenario: Written, read, rewritten

- **GIVEN** a step declaring two references
- **WHEN** the pack is read and written again
- **THEN** the step file still carries the same two references in the same key

#### Scenario: A step file carrying the key with no skills folder

- **GIVEN** a step file declaring `references:` in a pack carrying no skills
- **WHEN** the pack is validated
- **THEN** the declaration is reported as dangling rather than dropped
