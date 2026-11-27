# Spec Delta

## Purpose

Defines the shipped generic pack: a folder of agent definitions whose only job is to carry out a task by
following a skill, with no knowledge of the task's domain. It is the case that proves the reference,
script and tool channels work together for a skill the pack has never seen.

## ADDED Requirements

### Requirement: The interpreter pack is task-agnostic

The repository SHALL ship a pack whose steps contain no domain knowledge: no task-specific nouns, no named
files, no assumed directory layout. The task arrives as the run's input and the skill to follow arrives as
a reference, so the same pack is usable for a task nobody anticipated when it was written.

#### Scenario: A skill it has never seen

- **GIVEN** a skill folder that the pack's authors never saw
- **WHEN** the pack is run against it with a task
- **THEN** the run proceeds through the pack's steps without any pack file changing

#### Scenario: Nothing in the pack names the task

- **WHEN** the pack's step prompts are read
- **THEN** they speak of the skill, the task and the working directory, and of no subject matter

#### Scenario: The task arrives as input

- **WHEN** the run is started
- **THEN** the task text is the run input and the skill is a reference, not an edit to the pack

### Requirement: Its control loop is types and conditions, not flow syntax

The pack SHALL express its progression through named types and step conditions, because the format carries
no ordering, no loop and no branch construct. Its steps SHALL be legible as a progression — establish what
the skill says, act on the task, establish whether the skill's own criteria are met, report — with the
planner deciding order from what each step produces and requires.

#### Scenario: A step's output is the next step's precondition

- **WHEN** the pack's steps are read
- **THEN** each later step names a type or condition an earlier step produces, and no step depends on
  having run second in a list

#### Scenario: A step is skipped because its condition is unmet

- **WHEN** the condition a step needs was never established
- **THEN** that step is not selected, and the run's report shows the unmet condition rather than a silence

#### Scenario: The progression is stated in the pack

- **WHEN** a reader inspects the pack
- **THEN** the progression is visible in the steps' declared types and conditions, not only in prose

### Requirement: The pack asks for capabilities and the deployment answers

A step SHALL declare the tool-group roles and references it needs rather than assuming them. Where a
declared capability is not registered in the deployment, the run SHALL say which capability is missing and
why, and the pack's outcome SHALL state what it could not do instead of the model inventing a way to do it.

#### Scenario: A needed capability is absent

- **GIVEN** a deployment where a role the pack declares is not registered
- **WHEN** the run reaches the step that needed it
- **THEN** the run names the missing capability and the pack's report says the task is incomplete because
  of it

#### Scenario: A skill asks for something the tier forbids

- **WHEN** following the skill would require a declaration the selected tier does not allow
- **THEN** the run is blocked by the tier check, naming the pack and the role, before anything executes

#### Scenario: The pack declares nothing extra

- **WHEN** a run grants only the default tool set
- **THEN** the pack still completes the parts of the skill reachable with reads and reports the rest as
  unavailable

### Requirement: Repetition comes from re-planning, not from a loop

Where a skill calls for work to continue until a condition holds, the pack SHALL rely on the platform's
re-planning path rather than on an iteration construct the format does not have. A step that reports new
information SHALL be able to ask for the plan to be rebuilt, and the platform's guard against rebuilding
into the same step immediately SHALL be stated in the pack's notes.

#### Scenario: New information changes what to do next

- **WHEN** a step reports something the plan did not anticipate
- **THEN** a re-plan is requested and the next step is chosen against the new state

#### Scenario: Re-planning cannot spin

- **WHEN** the only applicable step after a re-plan is the one that asked for it
- **THEN** the run does not loop indefinitely, and the outcome records that the plan stalled

#### Scenario: A plan cannot be built at all

- **WHEN** no plan reaches the goal from the current state
- **THEN** the run records that as its own outcome, naming the goal's unmet preconditions, and not as a
  generic failure

### Requirement: The generic pack and a concrete instance both ship

The repository SHALL ship the generic pack and at least one narrower pack that uses the same channels for a
specific kind of work, so a reader can see the difference between the mechanism and one use of it, and so a
failure in the generic case is distinguishable from a failure in the instance.

#### Scenario: The instance is smaller than the generic pack

- **WHEN** the two packs are compared
- **THEN** the instance has fewer steps and names a domain, while the generic pack names none

#### Scenario: One fails and the other does not

- **WHEN** a run of one succeeds and a run of the other fails
- **THEN** the failure is attributable to that pack's steps rather than to the shared channels, because the
  succeeding pack exercises the same channels

#### Scenario: A pack with no skill still runs

- **WHEN** the generic pack is run with a source that carries no skill
- **THEN** it reports that no skill was available rather than failing, which is the degradation path a
  reader can check offline

### Requirement: Its claims are separated by how they were proven

What the pack demonstrates SHALL be recorded in two parts: what an offline run establishes about offered
tools, resolved references and selected steps, and what only a live model call establishes about a skill
being followed to an answer.

#### Scenario: An offline claim

- **WHEN** an offline run shows the steps selected and the references resolved for a skill
- **THEN** it is recorded as an offered-capability claim with the command that reproduces it

#### Scenario: A live claim

- **WHEN** a live run reads a skill and completes its steps to an answer
- **THEN** it is recorded as endpoint-gated, with the exact command, the skill used and the tier
