# Spec Delta

## Purpose

Whether the builder's own process exists in the format the builder builds — which decides what an
improvement loop has to mutate, and whether the builder's process is written down anywhere at all.

## ADDED Requirements

### Requirement: The builder's process is expressed as agent specs

The builder SHALL hold an agent spec for each of the three processes it performs: building skills from a
process specification, building a multi-agent process of collaborating specs, and building one
multi-step spec.

#### Scenario: A loop asks for the builder's process

- **WHEN** a caller asks the builder what process it performs
- **THEN** it receives loadable agent specs from the builder module, and not only Java annotated with
  planner metadata

#### Scenario: A pack is loaded and deployed

- **WHEN** each builder pack is loaded, validated and deployed behind a stand-in invoker
- **THEN** all three succeed, and deleting a pack fails a build rather than leaving a file nothing reads

#### Scenario: A step names an input it never uses

- **WHEN** a builder pack step declares an input type and its prompt references none of them
- **THEN** the pack is rejected, because a step that cannot see its input answers every task the same way

### Requirement: A builder pack holds cognitive stages only

A builder pack SHALL contain only stages whose work is performed by a prompt, and every deterministic
check in the builder SHALL remain host code outside the pack.

#### Scenario: A pack tries to hold a deterministic check

- **WHEN** a builder pack declares a step that performs a deterministic check rather than a prompt
- **THEN** validation rejects the pack, and the check stays where it already runs

#### Scenario: A step emits a spec

- **WHEN** a step's job is to produce a specification
- **THEN** the step emits text or JSON and host code parses and validates it, because a spec is not a
  type a step output can declare

### Requirement: A builder pack declares a named type for what it returns

A builder pack's step outputs SHALL be named types the pack declares, and SHALL NOT be the framework's
polymorphic background-message type.

#### Scenario: A step output is declared

- **WHEN** a builder pack step produces a value another step consumes
- **THEN** the pack declares a type for it, and the background-message type appears nowhere in the pack

#### Scenario: The rule is proven to be able to fail

- **WHEN** a conforming pack and a violating pack are both checked
- **THEN** only the violating one is rejected, so the rule is not satisfied by a name it happens to match
