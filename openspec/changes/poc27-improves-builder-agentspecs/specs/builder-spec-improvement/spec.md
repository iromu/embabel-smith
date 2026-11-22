# Spec Delta

## Purpose

What an improvement loop is allowed to change when it improves the builder rather than somebody else's
pack — the surface, the reason the rest is out of reach, and the boundary written down where a run can be
refused against it.

## ADDED Requirements

### Requirement: The improvement object is the builder's own spec

The improvement loop's second leg SHALL propose candidates that are the builder's own agent specs with
their step prompts rewritten, and SHALL NOT propose a pack assembled for the purpose inside the harness.

#### Scenario: A candidate is proposed

- **WHEN** a generation proposes a candidate
- **THEN** it is a builder pack whose step prompts differ from its parent's, and every other step field —
  name, declared inputs, declared output, and the pack's declared types — is identical

#### Scenario: A candidate would change more than a prompt

- **WHEN** a proposed candidate differs from its parent in anything other than step prompt text
- **THEN** it is refused, because one generation that rewrites everything attributes its result to nothing

### Requirement: Deterministic gates are beyond the mutable surface

Logic that decides rather than reasons SHALL sit outside the surface a generation can write to, and that
exclusion SHALL follow from the format rather than from a policy a run could relax.

#### Scenario: A generation reaches for a gate

- **WHEN** a generation would need to alter a validator, a gate chain, a scorer or a publisher to make a
  candidate pass
- **THEN** it cannot, because those are host code and a generation writes only spec files

#### Scenario: The exclusion is explained by its cause

- **WHEN** the mutable and fixed surfaces are recorded
- **THEN** the record states that a spec step without a prompt is invalid, so no deterministic check can
  be expressed inside a spec at all, and cites the fixed artifacts already listed rather than restating
  them

### Requirement: The builder's agent flow stays host code while its spec is mutable

The builder's executing flow SHALL keep running from host code, and its agent-spec form SHALL be mutable
data that a loop can improve.

#### Scenario: A generation improves the builder's spec

- **WHEN** a generation rewrites a builder pack's prompt and the candidate loses the gate
- **THEN** the shipped build path is unaffected, because it never read the pack the generation proposed

#### Scenario: The spec form is not decoration

- **WHEN** the builder's packs are loaded and deployed in a test
- **THEN** they are accepted by the validator and by the platform, so the mutable surface is a real
  artifact and not an unused copy
