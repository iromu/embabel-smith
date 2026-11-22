# Spec Delta

## Purpose

Which driver runs a build, and what is allowed to differ between drivers — the question that decides
whether the builder's process being written as a pack has any consequence for what `smith build` does.

## ADDED Requirements

### Requirement: A build names the driver that ran it

A build SHALL run on exactly one flow, Java or pack, chosen explicitly per invocation, and SHALL report which
one produced the result.

#### Scenario: A build reports its flow

- **WHEN** a build completes on either flow
- **THEN** the report names the flow that ran, so a recorded result can be attributed to a driver

#### Scenario: No flow is requested

- **WHEN** a build is invoked without naming a flow
- **THEN** it runs the default and says that it used the default rather than an explicit choice

### Requirement: Deterministic gates are identical across flows

Every deterministic check on a build — validation, security scanning, lifecycle stamping, publishing — SHALL
run on both flows, in the same order, as host code outside any pack.

#### Scenario: The same fixture is built both ways

- **WHEN** one input is built on the Java flow and on the pack flow with the same model answers
- **THEN** the sequence of gates that ran is identical, and any difference in outcome is attributable to the
  model call and not to a gate one flow skipped

#### Scenario: A pack tries to own a gate

- **WHEN** a pack's step performs a deterministic check rather than a prompt
- **THEN** the pack is rejected and the check stays outside the pack, where both flows can see it run

### Requirement: A pack flow does not fall back

A pack flow that fails SHALL fail the build with the stage that stopped it, and SHALL NOT retry the work on
the Java flow.

#### Scenario: The pack cannot be deployed

- **WHEN** the pack flow cannot load or deploy its pack
- **THEN** the run stops with a named reason before spending a model call, and does not report a success

#### Scenario: The pack flow's output fails a gate

- **WHEN** a pack-flow build reaches a gate and is refused by it
- **THEN** the build is reported as refused, and no Java-flow attempt is made behind the report

### Requirement: The shipped default moves only on recorded evidence

The default flow SHALL change only when four recorded measurements are present in the change that made the
capability available: that a declared tool group yields a callable tool on a running platform, that the pack
flow's validated-output rate is reported against the Java flow's, that calls per build are reported for
both, and that the scorer stays outside every builder pack.

#### Scenario: A capability lands without the measurements

- **WHEN** the pack flow is available but the four measurements are not recorded
- **THEN** the default remains the Java flow, and the capability is reachable only by explicit request

#### Scenario: A single-step pack declares a tool group

- **WHEN** the shipped single-spec build runs from a one-step pack that declares a tool group
- **THEN** it pays the same single model call it paid as Java, and gains the ability to call the validator
  and re-draft against what it returns
