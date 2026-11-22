# skill-build-gate Specification

## Purpose
What the skill target's lift gate is entitled to trust before it lets a skill publish: the documents
it security-scans, the minimum validity of the model-drafted eval suite it measures against, and who
may set the lift the suite must clear.

## Requirements

### Requirement: The security scan covers every model-authored document a skill publishes

The skill build's static security scan SHALL examine each document it publishes from model output —
the `SKILL.md` and the `evals.json` suite — and not the `SKILL.md` alone. A sidecar the builder
generated itself is not model output and is not scanned.

#### Scenario: An injection marker sits in an eval expectation

- **WHEN** the drafted suite contains a finding the scanner rates critical and the `SKILL.md` is clean
- **THEN** the build blocks at the security stage and nothing is written to the publish target

#### Scenario: Both documents are clean

- **WHEN** neither the skill body nor the suite carries a finding
- **THEN** the build proceeds to the lifecycle stamp and the publish, as it does today

### Requirement: A drafted scenario must carry an expectation

Every scenario in the model-drafted suite SHALL state at least one expectation. A suite containing a
scenario with none SHALL block the build at the eval stage, naming those scenarios, before either arm
of the benchmark runs.

#### Scenario: The drafted suite contains an empty scenario

- **WHEN** one scenario's expectation list is empty while the suite as a whole is not
- **THEN** the build blocks at the eval stage naming the offending scenario ids and no with-skill or
  baseline call is made

#### Scenario: Every scenario is measurable

- **WHEN** each drafted scenario carries at least one expectation
- **THEN** both arms run and the lift gate decides the build as before

### Requirement: The lift the gate requires is settable from the command line

The build command SHALL accept the minimum skill lift as an option and pass it to the skill gate,
defaulting to the value the gate uses today.

#### Scenario: A run names a minimum lift

- **WHEN** a skill build is launched with the lift option set above the default
- **THEN** the gate compares the measured lift against the named value and the blocked report states
  that value as the required minimum

#### Scenario: A run names no minimum

- **WHEN** a skill build is launched without the option
- **THEN** the gate uses the default minimum and any lift that beats the baseline publishes

#### Scenario: The option is given on an agent-spec build

- **WHEN** the lift option is supplied with an agent-spec target
- **THEN** the command accepts it and that build's outcome is decided by its score gate alone
