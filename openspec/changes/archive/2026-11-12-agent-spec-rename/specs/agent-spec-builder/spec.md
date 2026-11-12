# Spec Delta

## ADDED Requirements

### Requirement: Builder runs as a single agent
The builder's build component SHALL be a single Embabel agent whose goal is that a validated
agent spec exists on disk, reached by a deterministic pipeline of code-backed actions
(compile, validate, write); the model is invoked only inside the compile action.

#### Scenario: Build is one agent's plan
- **WHEN** the builder is given a coherent description
- **THEN** the compile → validate → write steps execute as actions of that single agent, whose
  goal is the written agent spec

#### Scenario: Model-gated compile, pure-Java tail
- **WHEN** the build pipeline runs
- **THEN** only the compile action calls the model, and validate and write execute as pure
  Java in deterministic order
