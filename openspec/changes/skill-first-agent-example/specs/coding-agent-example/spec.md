# Spec Delta

## Purpose

Ships one worked example of a general coding agent as a folder of agent specs run by the no-code
runtime, so the format, skill-hosting and tool-addon contracts above have a case a reader can run.

## ADDED Requirements

### Requirement: The repository ships a coding-agent example folder

The repository SHALL carry a folder of hand-authored agent definitions under `agent-specs/` whose goal
is a coding task answered against a working directory. It holds several agent definitions, each a valid
pack in its own right, and it carries skills both for individual agents and shared by the folder. Every
definition validates under the format checks, including the reference checks. The whole folder is pure
data: no build step, no compiled class, nothing installed.

#### Scenario: The folder validates offline

- **WHEN** each agent definition in the folder is validated with no endpoint configured
- **THEN** validation passes and every declared reference resolves, whether to the agent's own skills or
  the folder's shared ones

#### Scenario: The definitions declare what they need

- **WHEN** the definitions' steps are read
- **THEN** they name the file or command role and the skills they rely on, rather than assuming a default

#### Scenario: A shared skill serves more than one definition

- **GIVEN** a skill in the folder's shared `skills/` directory
- **WHEN** two of the definitions reference it
- **THEN** both resolve to that one folder

### Requirement: The example runs through the same reader as every other pack

The example SHALL be run by the ordinary run command against an ordinary spec source, with no dedicated
launcher, no builder involvement and no studio process. A definition that needs only the default tool set
SHALL be documented with no configuration at all; where a definition needs a group that is off by default,
its documented command names the switch and the tier, because those two are what decide whether its
declaration is allowed and answered.

#### Scenario: The read-only path needs nothing configured

- **GIVEN** a deployment with no tool property set anywhere
- **WHEN** the definition that declares only the file role runs at the default tier
- **THEN** the run reaches its exported goal, because the group it declares registers by default

#### Scenario: Run against a working directory

- **WHEN** the example is run with a working directory and a coding request, with the switches its
  documented command names and at the tier it states
- **THEN** the run reaches an exported goal and reports the answer as a goal output

#### Scenario: Run with a needed group switched off

- **WHEN** the example is run with the group its steps need explicitly disabled
- **THEN** the run reports the roles that resolved to nothing, as disabled by configuration rather than
  unknown

#### Scenario: Run at a tier that forbids its declarations

- **WHEN** the example is run at a tier that does not allow a role it declares
- **THEN** the run is blocked before execution and says which definition caused it

### Requirement: The example's claims are separated by how they were proven

What the example demonstrates SHALL be recorded in two parts: what an offline run establishes, and what
only a live model call establishes. A capability shown offline by inspecting what was offered is not
reported as a demonstrated end-to-end run.

#### Scenario: An offline claim

- **WHEN** an offline run shows the offered tool list and the reference resolution for the example's
  steps
- **THEN** it is recorded as an offered-capability claim, with the check that reproduces it

#### Scenario: A live claim

- **WHEN** a live run has the model actually read a file and use a skill
- **THEN** it is recorded separately, named as endpoint-gated, with the command that re-runs it

### Requirement: An example also runs with no addon at all

The repository SHALL also carry an agent spec whose step declares a tool group that an unmodified runtime
already resolves — the arithmetic group — so the claim that a declared group reaches a model can be shown
without the coding addon, without a skill, and without code this change adds. Its documented run command
SHALL name the tier that allows the declaration, since the arithmetic role is not in the default tier's
allowlist.

#### Scenario: Run at the tier that allows it

- **GIVEN** a run with no tool property set at all
- **WHEN** the arithmetic example runs at the tier its run command names
- **THEN** the step is offered the arithmetic group and the run reaches its exported goal

#### Scenario: Run at the default tier

- **WHEN** the same pack runs without naming a tier
- **THEN** the run is blocked before execution, naming the pack and the arithmetic role

#### Scenario: The pack needs nothing this change adds

- **WHEN** the arithmetic pack is validated and run on a build predating this change
- **THEN** both succeed, which is what separates a working channel from a working addon

#### Scenario: No skill is involved

- **WHEN** the arithmetic pack's steps are read
- **THEN** none declares a reference, so a failure there cannot be blamed on skill loading
