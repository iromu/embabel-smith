# Spec Delta

## Purpose

Where the self-improvement engine lives, which module a caller must depend on to reach it, and what a
caller has to drag onto its classpath to do so.

## ADDED Requirements

### Requirement: The improvement engine is builder logic

The self-improvement engine — detection, gating, approval, publish policy, lifecycle transitions and the
loop that drives them — SHALL live in `embabel-smith-builder`, and no class of it SHALL depend on
`embabel-smith-studio`.

#### Scenario: A POC scores a candidate

- **WHEN** a module under `poc/` constructs a gate over a frozen task set
- **THEN** it depends only on `embabel-smith-builder` and does not exclude web, JPA, database-driver or
  docker-compose artifacts to start

#### Scenario: The studio wants to run a loop

- **WHEN** the studio schedules an improvement iteration
- **THEN** it calls into the builder and keeps only scheduling, HTTP exposure and persistence

#### Scenario: A caller reaches for persistence through the engine

- **WHEN** the publish policy needs a candidate written back
- **THEN** it calls a store abstraction declared in the builder, and the studio supplies the adapter over
  its own store

### Requirement: One definition of the default pack version

A default pack version SHALL NOT be read from a persistence entity by logic that is not persisting, and
exactly one definition of its value SHALL exist in the product.

#### Scenario: The refiner needs a version when a spec declares none

- **WHEN** a pack is refined and its manifest version is absent
- **THEN** the default comes from a non-persistence declaration
- **AND** the persistence entity's public constant resolves to the same single definition
