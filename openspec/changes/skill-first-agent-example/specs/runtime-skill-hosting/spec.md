# Spec Delta

## Purpose

Defines what the no-code runtime does with the skills a pack declares: which model calls are offered
them, what happens when a skill cannot be loaded, and what a run says out loud about both.

## ADDED Requirements

### Requirement: A step that names a skill is offered its tools

When a run executes a step that declares a reference, the model call for that step SHALL be offered the
activation tools of the named skill. The offered names are the reference's own prefixed names, so the
names a test or a log sees are derived from the reference, not from the skill folder.

#### Scenario: The step asks and the pack carries

- **GIVEN** an ingested pack whose step declares `references: [repo-notes]` and whose `skills/` folder
  carries `repo-notes`
- **WHEN** the plan executes that step
- **THEN** the request sent for it lists that skill's activation tools under the reference's prefix

#### Scenario: Another step in the same pack does not ask

- **GIVEN** the same pack and a second step that declares no references
- **WHEN** the plan executes the second step
- **THEN** its request lists none of the skill's tools

#### Scenario: The skill offers progressive disclosure, not its whole contents

- **WHEN** the skill's tools are offered
- **THEN** the model is given the load-and-read surface rather than the skill's full text inlined into
  every prompt

### Requirement: Declaring a skill does not change the plan

Skill references SHALL not enter planner reasoning. The plan a pack and goal produce stays the step
sequence it produced before this change; a reference changes only what a selected step's model call can
reach, never which steps the planner selects.

#### Scenario: Same pack, same plan

- **GIVEN** a pack identical in every respect except that one step now declares a reference
- **WHEN** the same input is run against both
- **THEN** both runs report the same step sequence

### Requirement: A skill that cannot be loaded is reported, not swallowed

A skill folder the loader refuses SHALL be named in the run's output, and the run SHALL NOT report the
skill as available. A pack whose every skill was refused runs on without them rather than failing,
because a pack is still runnable without its optional capability.

#### Scenario: One unloadable folder among readable ones

- **GIVEN** a pack carrying two skills, one of which the loader refuses
- **WHEN** the pack is ingested
- **THEN** the refused folder is named in the run's output and the other skill's tools are still offered

#### Scenario: Every skill in the pack is refused

- **GIVEN** a pack whose only skill folder the loader refuses
- **WHEN** the pack is run
- **THEN** the run proceeds, names the refused folder, and offers the step no skill tool

### Requirement: A reference resolves against its agent, then the shared set

A reference SHALL resolve first against the skills in its own agent definition's directory, then against
a `skills/` directory shared by the folder holding that agent. A name carried only by an unrelated agent
or an unrelated folder MUST NOT resolve, and is reported as dangling rather than silently resolving
through a sibling.

#### Scenario: The name is in the shared folder

- **GIVEN** an agent with no skills of its own, inside a folder whose shared `skills/` carries the name
  its step declares
- **WHEN** the step executes
- **THEN** the skill's tools are offered to that step

#### Scenario: The name belongs to a sibling agent only

- **GIVEN** a folder holding two agents, one of which carries the skill the other's step names, and no
  shared folder
- **WHEN** the second agent's step executes
- **THEN** nothing is offered from the sibling's skill and the declaration is reported as dangling

#### Scenario: A shared name is shadowed

- **GIVEN** an agent carrying its own copy of a name the shared folder also carries
- **WHEN** its step executes
- **THEN** the agent's own copy is the one offered, and the run says which folder served it

### Requirement: A carried skill's scripts are not executed while the switch is off

A run SHALL NOT execute a skill's scripts while script execution is switched off, which is the default, and
the run's output SHALL say that the skill carries scripts and that this run is not executing them rather
than leaving the absence to look like a refusal from somewhere else. When the switch is on, execution
follows the script-execution capability; this requirement covers what a reader sees when it is not.

#### Scenario: A carried skill ships scripts, switch off

- **GIVEN** a pack carrying a skill with a `scripts/` directory
- **WHEN** the pack is run with no script property set
- **THEN** no script tool is offered, and the output states that scripts exist here and are not being
  executed, naming the switch that would change that

#### Scenario: Absence is not confused with a policy block

- **WHEN** a step is offered no script tool
- **THEN** the run's explanation is the switch state, not a trust-tier message

#### Scenario: Scripts on, and a step that was never meant to run them

- **GIVEN** script execution switched on
- **WHEN** a step that names no script-bearing reference executes
- **THEN** it is offered no script tool, though another step in the same run may be
