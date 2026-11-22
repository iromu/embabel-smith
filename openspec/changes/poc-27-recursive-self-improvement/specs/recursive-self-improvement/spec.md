# Spec Delta

## Purpose

Defines what a self-improving generation loop may change, what judges the result, and what a change
must demonstrate before it may be called an improvement rather than noise.

## ADDED Requirements

### Requirement: Mutable and fixed sets are declared before the first generation

Before any generation runs, the harness SHALL declare which artifacts an improver is permitted to
mutate and which are fixed for the duration of the run. An attempted mutation of a fixed artifact
SHALL stop the run rather than be accepted and scored.

#### Scenario: Mutation targets a fixed artifact

- **WHEN** a generation proposes a change to an artifact listed in the fixed set
- **THEN** the run stops, names the artifact, and records no score for that generation

#### Scenario: Mutation targets a permitted artifact

- **WHEN** a generation proposes a change only to artifacts in the mutable set
- **THEN** the change is versioned as a new variant and passed to the referee for scoring

### Requirement: Candidates are scored against a frozen task set

The referee SHALL score candidates only against a task set that is authored by a human, checked in
before tuning begins, and never written by the loop. A candidate SHALL NOT be able to add, remove, or
edit the tasks it is judged on.

#### Scenario: Candidate proposes additional tasks

- **WHEN** a candidate's output includes proposed evaluation tasks
- **THEN** those tasks are excluded from scoring and their exclusion is recorded in the report

#### Scenario: Outer loop starts without a recorded baseline

- **WHEN** the paid outer loop is started and the frozen task set has no recorded baseline score
- **THEN** the loop refuses to start and states that the baseline must be recorded first

### Requirement: Generated code never executes inside the referee

The referee SHALL NOT load a skill folder or execute generated script code. Generated scripts SHALL
run only inside the candidate being evaluated, under the script engine's own limits.

#### Scenario: Candidate skill carries a generated script

- **WHEN** the referee scores a candidate whose skill includes a generated JavaScript script
- **THEN** the script executes in the candidate's execution context and the referee opens no script
  context of its own

### Requirement: An improvement must exceed measured run-to-run noise

A generation's result SHALL count as an improvement only when its paired delta against the previous
generation exceeds a noise floor measured by repeated scoring runs before any tuning. A delta within
that floor SHALL be reported as no improvement.

#### Scenario: Delta falls inside the noise floor

- **WHEN** a candidate scores higher than its parent by less than the recorded floor
- **THEN** the gate rejects it and the report states "no improvement" rather than a percentage

### Requirement: Recursion is measured across generations

The harness SHALL run at least two generations and report improvement per generation. A score gain
observed within a single generation SHALL NOT be reported as recursive improvement.

#### Scenario: Only one generation completes

- **WHEN** the budget is exhausted after a single generation
- **THEN** the result is reported as one round of hill-climbing and the recursive claim is marked
  untested

#### Scenario: A later improver produces no better candidates

- **WHEN** the generation-N improver yields generation-N+1 candidates no better than the generation-N-1
  improver produced
- **THEN** the report states that the improvement rate is flat and gives the per-generation figures

### Requirement: Every generation is retained

The harness SHALL retain every variant it evaluates, each with its parent and its score, and selection
SHALL be able to consider any retained variant rather than only the current best.

#### Scenario: A variant scores below its parent

- **WHEN** a generated variant scores below the variant it was derived from
- **THEN** it is retained in the archive with its score and its parent recorded

### Requirement: Detection policies are judged offline against labelled traces

A generated improvement policy that decides which recorded failures count as defects SHALL be scored
against a held-out, human-labelled trace corpus, reporting precision and recall separately and making
no model call.

#### Scenario: Policy trades precision for recall

- **WHEN** a policy flags most genuine defects but also many non-defects
- **THEN** the report shows its precision and recall separately instead of a single blended score

### Requirement: A process description yields multiple validated agent specs

From a process description the harness SHALL produce a multi-agent set of at least two agent specs,
and SHALL validate their hand-offs before any of them is scored.

#### Scenario: Hand-off validation fails

- **WHEN** the generated agent specs do not cover each other's declared inputs, or form a cycle that
  repair does not resolve
- **THEN** the candidate is not scored and the failing hand-off is named in the report

### Requirement: A generated skill is credited only when its script runs

A generated skill SHALL be credited with its script only when that script executes successfully under
the script engine; a script that errors or is refused SHALL be reported as a defect of the skill.

#### Scenario: Generated script fails at run time

- **WHEN** a candidate skill's generated script exits with an error during evaluation
- **THEN** the skill is scored without script credit and the failure is attributed to the skill

### Requirement: Human control and cost bounds stay in force

Candidates SHALL hold for approval rather than publish automatically, the loop SHALL NOT push, and the
number of candidates held SHALL be reported. The run SHALL carry a budget expressed in model calls and
in generations, declared before the first call.

#### Scenario: A candidate clears the gate

- **WHEN** a candidate passes every check
- **THEN** it is held pending approval, nothing is published, and the held count increases by one

#### Scenario: Budget is exhausted mid-generation

- **WHEN** the declared model-call budget runs out during a generation
- **THEN** the run stops at that boundary and states which measurements were not taken
