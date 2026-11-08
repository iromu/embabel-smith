# product-module-layout Specification

## Purpose

The production module layout of Embabel Smith: where product code lives, how the modules relate, and the rules that keep the product decoupled from the frozen POC evidence. This capability owns the build-structure invariants that every later product change (02–06) builds on.

## Requirements

### Requirement: Production modules live under a prod aggregator
The project SHALL contain a `prod/` Maven aggregator module (`embabel-smith-prod`) registered in the root POM, holding the production modules `embabel-smith-skillsource`, `embabel-smith-runtime`, `embabel-smith-builder`, and `embabel-smith-studio` (folders `prod/embabel-smith-*/`).

#### Scenario: Aggregator builds
- **WHEN** the root build (`mvn verify` from the project root) runs
- **THEN** the `prod/` aggregator and its four child modules are built as part of the reactor

#### Scenario: Module skeleton is valid
- **WHEN** a production module skeleton is created with its POM, empty `src/main/java` and `src/test/java` trees, and a README
- **THEN** `mvn -pl prod/<module> verify` succeeds without any production code present

### Requirement: Production modules must not depend on POC app modules
A production module's dependencies SHALL NOT include any `com.embabel.smith:poc-*` artifact. Production modules MAY depend on `smith-common`, Embabel platform artifacts, and sibling `prod/` modules.

#### Scenario: POC dependency is rejected at build time
- **WHEN** a production module's POM declares a dependency on a `poc-*` artifact
- **THEN** the build of that module fails with a clear enforcer error naming the banned artifact

#### Scenario: Legal dependencies build
- **WHEN** a production module depends on `smith-common` and/or sibling production modules
- **THEN** the build succeeds

### Requirement: POC modules are frozen evidence
POC modules under `poc/` SHALL NOT be modified to serve product needs. Product capabilities are obtained by extracting (copy + generalize) proven POC logic into production modules; the POC modules remain as-is, runnable evidence.

#### Scenario: Product work does not touch POCs
- **WHEN** a product change (02–06) is implemented
- **THEN** no file under `poc/` is created, modified, or deleted by that change

### Requirement: Extracted logic keeps the POC test discipline
Logic extracted from POCs into production modules SHALL remain plain, offline-testable classes: no Spring Boot launcher is required to unit-test it, and any model call is endpoint-gated so `mvn test` passes without a live model.

#### Scenario: Offline tests pass without a model
- **WHEN** `mvn test` runs on a production module that contains extracted logic
- **THEN** all offline tests pass with no model endpoint configured
