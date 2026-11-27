# Spec Delta

## Purpose

Defines tool addons: separately-built libraries that publish tools under named roles, how a deployment
turns them on, and what a run says about which roles actually reached a model.

## ADDED Requirements

### Requirement: Tools ship as separately-built addons

Tools SHALL live in their own modules, one submodule per capability family, rather than inside the
process that runs them. An addon declares the tools it offers and the role names those tools answer to,
and it depends on neither the runtime nor the studio, so any of them can be added to or removed from a
deployment without changing host code. The first submodule is the coding family; later families are
additional submodules, not edits to the first.

#### Scenario: An addon is added to a deployment

- **GIVEN** a deployment that does not name the addon
- **WHEN** its artifact is placed on the deployment's classpath
- **THEN** no host source changes and no host rebuild is needed for the addon to be recognized as
  present

#### Scenario: A second family arrives

- **WHEN** a new capability family is added
- **THEN** it is a new submodule and the coding submodule is unchanged

#### Scenario: No reverse dependency

- **WHEN** the module graph is inspected
- **THEN** no addon depends on the runtime, the studio or the builder

### Requirement: A default set registers without being asked

The runtime SHALL register a documented set of tool groups at start-up with nothing configured, drawn from
the role names its default trust tier already allows, so that a pack declaring one of them reaches a real
tool on an untouched deployment. Registration SHALL NOT grant reach by itself: a pack's declaration is
still checked by the tier gate, and a pack that declares nothing is offered nothing.

#### Scenario: A default group with nothing configured

- **GIVEN** a deployment with no tool property set
- **WHEN** a step declares a role in the documented default set and the tier allows it
- **THEN** the role resolves and the step is offered the tools

#### Scenario: A group outside the default set

- **GIVEN** the same deployment with nothing configured
- **WHEN** a step declares a role that is not in the default set
- **THEN** the role resolves to nothing and the run names it as not registered, with the switch that would
  register it

#### Scenario: Registration is not permission

- **GIVEN** a default group that is registered
- **WHEN** a pack declares its role at a tier that does not allow it
- **THEN** the run is blocked before execution, naming the pack and the role

### Requirement: Every group has a switch

Each tool group SHALL be gated by a property naming it, defaulting to that group's documented value and
following the switch convention the deployment's other optional features use, so a default-on group can be
turned off and a default-off group turned on without a code change. A group's settings SHALL sit under the
same prefix as its switch.

#### Scenario: A default-on group is switched off

- **WHEN** the group's property is set false and a step declares its role
- **THEN** the role resolves to nothing and the run says it was disabled by configuration, not that it is
  unknown

#### Scenario: A default-off group is switched on

- **WHEN** the group's property is set true and a step declares its role within a tier that allows it
- **THEN** the role resolves and the step is offered the tools

#### Scenario: No property is set anywhere

- **WHEN** a run starts with no tool property present
- **THEN** each group is at its documented default and the report shows that state

#### Scenario: A switch for something not installed

- **WHEN** a property enables a group whose addon is not on the classpath
- **THEN** the run says there is nothing to enable, rather than reporting a registered group

### Requirement: Presence outside the default set is not reach

An addon on the classpath SHALL NOT register a role outside the documented default set until it is switched
on, so a dependency bump that pulls an addon in transitively cannot widen what a pack can reach.

#### Scenario: Present and default-off

- **GIVEN** an addon on the classpath whose groups are off by default
- **WHEN** a step declares the role that addon would have registered
- **THEN** the role resolves to nothing and the run reports that

#### Scenario: Pulled in by accident

- **WHEN** an addon arrives through a transitive dependency and its groups are not in the default set
- **THEN** no role it would register resolves, because nothing switched it on

### Requirement: A group can be named by configuration

A deployment SHALL be able to stand up a tool group without code: a preset selection, and a per-role list
of capability entries the addon resolves against the fixed set of capabilities it knows. An entry naming
something the addon does not know MUST be refused at start-up and named. A class name is not a valid
entry.

#### Scenario: A preset is selected

- **WHEN** the deployment names the coding preset
- **THEN** the file and command groups register under their documented roles

#### Scenario: An operator composes a role of their own

- **GIVEN** a configuration that names a role with the read and search capabilities
- **WHEN** a step declares that role
- **THEN** the role resolves to exactly those capabilities, and no write or command tool is offered

#### Scenario: A write switch is off

- **GIVEN** a deployment that disables writes for the file capability
- **WHEN** a step declares the file role
- **THEN** read tools are offered and no create, overwrite or edit tool is

#### Scenario: An entry the addon does not know

- **WHEN** the configuration names a capability the addon has no recipe for
- **THEN** start-up refuses and names the entry, rather than registering a group missing that piece

#### Scenario: A class name in place of a capability

- **WHEN** an entry is a fully-qualified class name
- **THEN** start-up refuses it, because a property must not be able to nominate any class on the classpath

#### Scenario: Two sources claim one role

- **GIVEN** a deployment where an upstream MCP-backed property also registers a group under a role the
  addon registers
- **WHEN** the run starts
- **THEN** the report names both sources, states which one the run will use, and the other is not silently
  preferred

### Requirement: Enabling an addon is stated at start-up

A run SHALL report the roles it registered and how each arrived — default, switched on, or switched off —
together with the addons it found but registered nothing from, and the reason. An operator diagnosing a
missing capability should not have to infer it from the absence of tool calls.

#### Scenario: One addon on, one present and off

- **WHEN** a run starts
- **THEN** the report names the registered addon with its roles and the other addon with the reason

#### Scenario: Nothing configured at all

- **WHEN** a run starts with no tool property present
- **THEN** the report lists the default set as the default set rather than printing nothing

### Requirement: The registered set is pinned

The set of roles a run can resolve with every switch at its documented default SHALL be asserted against a
pinned expectation, so that a platform or addon version change which registers an additional file-, command-
or network-backed group fails the build instead of quietly widening reach.

#### Scenario: An unexpected extra group

- **WHEN** a boot resolves a role outside the pinned set
- **THEN** the check fails and names the role

#### Scenario: A bump that turns something on by itself

- **WHEN** an upgrade registers a group that the pinned default set does not name
- **THEN** the check fails, because a default is a claim and not an accident

### Requirement: The roles an addon registers are the roles the tiers name

An addon SHALL register roles drawn from the trust vocabulary the runtime already applies, so that a
pack's declaration is checked by the existing pre-run tier gate. Registering a role SHALL NOT grant any
pack reach on its own: a pack that declares a role the selected tier does not allow is blocked before
anything executes, and a pack that declares nothing gains nothing.

#### Scenario: A declaration the tier forbids

- **GIVEN** an addon whose command role is enabled
- **WHEN** a pack declaring that role is run at a tier that does not allow it
- **THEN** the run is blocked before execution, naming the pack and the role

#### Scenario: The same declaration at a tier that allows it

- **WHEN** the same pack is run at the tier that allows the role
- **THEN** the run proceeds and the step is offered the tool

### Requirement: An unresolved declaration is visible

When an executed step declared a role that resolved to nothing, the run SHALL report it by role name. An
empty tool loop otherwise looks like a model that chose not to use a tool, and the two call for
different fixes.

#### Scenario: A role spelled wrongly

- **GIVEN** a step declaring a role no enabled addon registers
- **WHEN** the step executes
- **THEN** the run names the role that resolved to nothing

#### Scenario: A resolved role the model ignores

- **GIVEN** a step whose role resolved and was offered
- **WHEN** the model answers without calling it
- **THEN** the run reports no resolution failure
