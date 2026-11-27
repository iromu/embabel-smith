# Spec Delta

## Purpose

States how the planner behind a run is chosen, what the runtime claims about each choice, and what is
measured rather than assumed. The platform already supports more than one planning strategy and the runtime
already exposes a choice; what is missing is honesty about the options and evidence about which suits a pack
that follows a skill.

## ADDED Requirements

### Requirement: The planner is chosen per run and the named set is honest

A run SHALL select its planning strategy by name, from the set the platform implements, with a per-run
choice overriding a pack's default and an unknown name refused by listing the valid ones. The runtime
SHALL NOT describe an option as a distinct strategy when the platform routes it to the same algorithm as
another.

#### Scenario: A named strategy is selected

- **WHEN** a run names a supported strategy
- **THEN** that strategy plans the run and the report says which one ran

#### Scenario: An unknown name

- **WHEN** a run names a strategy the platform does not implement
- **THEN** the run refuses and lists the valid names, rather than falling back to the default quietly

#### Scenario: A strategy that is not what it sounds like

- **GIVEN** a named option that the platform implements as another option's algorithm
- **WHEN** a run selects it and the run is reported
- **THEN** the report says which algorithm actually planned the run

#### Scenario: Pack default and run flag disagree

- **WHEN** a pack declares a default strategy and the run names another
- **THEN** the run's choice applies and the report says it overrode the pack

### Requirement: Each strategy is measured against the skill-following pack

The supported strategies SHALL each be run against the generic pack, with the outcome recorded: the steps
selected, whether the run terminated when the skill's criteria were met, how many model calls the run cost,
and where it stopped. A strategy's suitability for skill following SHALL NOT be claimed on the strength of
its documentation alone.

#### Scenario: A strategy is recorded as untested

- **WHEN** a strategy has never been run against the pack on this repository's evidence
- **THEN** the documentation says so, rather than leaving the default to imply the alternatives were
  considered and rejected

#### Scenario: Two strategies on one pack

- **WHEN** the same pack and input are run under two strategies
- **THEN** the recorded difference is in steps selected, terminations and cost, in a form a reader can
  compare

#### Scenario: A strategy fails on the pack

- **WHEN** a strategy cannot reach the goal with the pack's steps
- **THEN** that failure is recorded as a finding about the pairing, naming the state it could not resolve

### Requirement: Planning choice never changes reach

A planning strategy SHALL NOT change what a pack is allowed to do. The tier check, the lifecycle exclusion
and the registered tool set apply identically under every strategy, and switching strategy SHALL NOT make a
blocked declaration runnable.

#### Scenario: The same blocked pack under another strategy

- **WHEN** a pack blocked by the tier is run under a different strategy
- **THEN** it is blocked in the same way, before execution

#### Scenario: A strategy sees the same packs

- **WHEN** a source holds several packs and the strategy changes
- **THEN** the set of packs the run may route through is unchanged

### Requirement: A run that cannot be planned says so precisely

When no plan reaches the goal from the current state, the run SHALL record that as its own outcome and name
the goal's preconditions that were never established, rather than reporting a failure with no steps.

#### Scenario: Goal preconditions unmet

- **WHEN** a run ends with no reachable plan
- **THEN** the outcome names the unmet preconditions and the step that would have established each, where
  such a step exists in the source

#### Scenario: A step exists but was unreachable

- **GIVEN** a source where the needed step belongs to a pack that was excluded
- **WHEN** the run ends unplanned
- **THEN** the report says the step was excluded rather than absent

### Requirement: Re-planning is the pack's iteration, and it is visible

Where a pack relies on re-planning to continue until a condition holds, a run SHALL record that a re-plan
was requested, by which step, and what changed. The platform's guard against immediately re-selecting the
step that asked SHALL be reflected in the outcome when it engages.

#### Scenario: A re-plan is requested

- **WHEN** a step asks for the plan to be rebuilt
- **THEN** the record shows the requesting step and the next step chosen after the rebuild

#### Scenario: The guard engages

- **WHEN** the only applicable step after a re-plan is the one that requested it
- **THEN** the run says the guard engaged rather than reporting an endless sequence of identical steps
