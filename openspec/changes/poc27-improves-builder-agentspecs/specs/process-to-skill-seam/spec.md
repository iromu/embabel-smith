# Spec Delta

## Purpose

Whether a process decomposition can reach the skill pipeline at all. Today the skill pipeline's two entry
points accept free text and nothing in the skill package mentions a process, so a decomposition has no
route into a skill — which is the missing half of "builds skills from a process specification".

## ADDED Requirements

### Requirement: A process decomposition reaches the skill pipeline

The builder SHALL build skills from a decomposed process, deriving one skill brief per stage from the
stage's own declared fields rather than requiring a caller to restate the process as prose.

#### Scenario: A decomposition is handed to the skill pipeline

- **WHEN** a caller passes a decomposed process to the skill pipeline
- **THEN** one skill is attempted per stage, and the description each receives is assembled from that
  stage's declared name, description, inputs and output

#### Scenario: Two stages are not one stage

- **WHEN** a process with two distinct stages is built into skills offline
- **THEN** the two stages are given two distinct skill briefs, so a per-stage skill is not the same skill
  built twice

#### Scenario: The gates are not rebuilt

- **WHEN** the process-driven path builds a skill
- **THEN** it runs the inspection and with/without-skill gates the existing text-driven path already
  runs, and adds no second gate of its own

### Requirement: The seam lives in the builder

The seam from a process to a skill SHALL live in the builder module, and the studio SHALL hold none of
its arithmetic.

#### Scenario: The seam is reached without the studio

- **WHEN** a caller builds skills from a process
- **THEN** it depends on the builder and on nothing that binds a port or opens a database

#### Scenario: The seam has a caller

- **WHEN** the seam is added
- **THEN** a named caller reaches it and an offline end-to-end test runs the whole chain with a stand-in
  model caller, so it is not a method only its own test invokes
