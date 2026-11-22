# Spec Delta

## Purpose

What stops an agent spec from being written when its steps cannot see their own inputs — the offline check
that stands between a generated spec and a paid call that cannot possibly answer differently.

## ADDED Requirements

### Requirement: A step that declares an input must be able to see it

An LLM-bound step that declares input types SHALL be rejected at validation when none of those inputs can
reach the model through its prompt, because a spec that cannot see its input produces one request
regardless of what it is asked.

#### Scenario: A step declares an input it never references

- **WHEN** a step declares an input type and its prompt names no template variable for it
- **THEN** validation rejects the spec, naming the step and the input that cannot reach the model

#### Scenario: A step interpolates the input it declares

- **WHEN** a step's prompt references the input it declares
- **THEN** this rule does not fail validation

#### Scenario: A step ignores its input deliberately

- **WHEN** a step is meant to produce the same output whatever it is asked
- **THEN** this rule does not fail validation, and the pack that proves it is part of the test set

#### Scenario: The published examples are revalidated

- **WHEN** every spec under `agent-specs/` and `examples/out/` is validated with this rule in force
- **THEN** all of them still pass
