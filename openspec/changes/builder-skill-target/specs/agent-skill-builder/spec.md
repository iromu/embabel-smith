# Spec Delta

## Purpose

The builder turns a business-process description into a portable Agent Skill (`SKILL.md`) and
validates it by comparing with-skill versus no-skill performance before publishing a skill folder.

## ADDED Requirements

### Requirement: Generate a well-formed Agent Skill
Given a business-process description, the builder SHALL produce a well-formed `SKILL.md` whose name
is fixed by the description and whose description and body are model-supplied, and SHALL reject a
skill whose frontmatter violates the Agent Skills constraints (name 1-64 lowercase-alphanumeric-or-
hyphen with no leading, trailing, or consecutive hyphens; description non-empty and at most 1024
characters).

#### Scenario: Coherent description produces a skill
- **WHEN** the builder is given a coherent description for the skill target
- **THEN** it produces a `SKILL.md` that passes the frontmatter validation

#### Scenario: Invalid frontmatter is rejected
- **WHEN** the generated frontmatter violates a constraint
- **THEN** the builder reports the specific constraint and does not publish the skill

### Requirement: Evaluate the skill with and without the skill
The builder SHALL run an eval suite of concrete scenarios against a general-purpose agent both with
the generated skill loaded and without it (the baseline), grade each run against the scenario's
verifiable expectations, and compute the with-skill pass rate and the no-skill pass rate.

#### Scenario: Both arms are measured
- **WHEN** the skill evaluation runs
- **THEN** every scenario is run once with the skill and once without it, and both pass rates are
  reported

### Requirement: Gate publication on skill lift
The builder SHALL compute the skill lift as the with-skill pass rate minus the no-skill baseline
pass rate, and SHALL NOT publish a skill whose lift does not meet a configured minimum.

#### Scenario: Positive lift publishes
- **WHEN** the skill lift meets the configured minimum
- **THEN** the skill is eligible for publication

#### Scenario: No lift blocks
- **WHEN** the with-skill pass rate does not exceed the baseline by the configured minimum
- **THEN** publication is blocked and the per-scenario grading is reported

### Requirement: Publish the skill folder
The builder SHALL publish the skill (its `SKILL.md` and `evals.json`) into an output source addressed
by the same URI grammar as the spec builder (`folder:` or `git:`), with the same collision and
no-push-by-default semantics.

#### Scenario: Folder publish
- **WHEN** the output is `folder:./skills`
- **THEN** the skill appears as `./skills/<skill-name>/` with its `SKILL.md` and `evals.json`

#### Scenario: Name collision in target
- **WHEN** the target already contains a skill with the same name
- **THEN** the builder reports the collision and does not silently overwrite; an explicit
  `--overwrite` flag replaces it
