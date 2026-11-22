# Spec Delta

## Purpose

The conditions under which a measured improvement means anything: room to improve into, a judge a reader
agrees with, a known noise floor, and proof that the thing under test was actually asked a different
question each time. Each is a precondition, checked before spend rather than explained afterwards.

## ADDED Requirements

### Requirement: Headroom is proven before a paid run

A paid improvement run SHALL be refused when the unmodified subject already satisfies every task in the
frozen set, because a saturated benchmark produces a flat derivative whatever the loop does.

#### Scenario: The subject passes everything

- **WHEN** the unmodified subject passes every task under the deciding arm
- **THEN** the run is refused with the score that caused the refusal, and no call is made

#### Scenario: The headroom check can say no

- **WHEN** an all-correct answer set is put to the headroom check
- **THEN** it reports no headroom, so a check that has only ever seen failures is not treated as proven

### Requirement: Judge disagreement is measured and removes its own authority

The scoring arm and an independent human-labelled arm SHALL both be recorded per task, and a task where
they disagree SHALL be excluded from the deciding score and reported by identifier.

#### Scenario: The arms disagree on a task

- **WHEN** the deterministic arm passes a task a human label fails
- **THEN** that task is excluded from the deciding score, its identifier is reported, and the smaller
  denominator is stated

#### Scenario: Agreement is a number, not an assumption

- **WHEN** a run reports its scores
- **THEN** it reports the count and identifiers of the tasks on which the two arms disagreed

### Requirement: The noise floor precedes the derivative

A run SHALL establish its noise floor by repeated scoring of an unchanged subject before comparing any
candidate, and SHALL report a measured floor at its measured value.

#### Scenario: Repeats agree exactly

- **WHEN** repeated scoring of the unchanged subject produces identical scores
- **THEN** the floor is recorded as zero and passed through as zero, not raised for safety

#### Scenario: Answers move between repeats

- **WHEN** repeated scoring of the unchanged subject produces different answers
- **THEN** the floor is the observed movement, and a candidate delta inside it is not counted as
  improvement

### Requirement: Distinct tasks must produce distinct answers

A run SHALL verify that eval tasks with different inputs produced different answers, and SHALL stop and
record why when they did not, rather than continuing to spend.

#### Scenario: Every task got the same answer

- **WHEN** two or more tasks with different inputs received an identical answer
- **THEN** the run stops with a named refusal and a recorded reason, before the remainder of its budget
  is spent

#### Scenario: The check is proven to be able to pass

- **WHEN** a stand-in that varies its answer and a stand-in that returns a constant are both run through
  the check
- **THEN** the constant one triggers the refusal and the varying one does not

### Requirement: Model spend is declared with a reason and a ceiling

A paid run SHALL declare its call ceiling as arithmetic from the task count, state the reason for the
spend beside the number, and apply a wall-clock ceiling as well as a call ceiling.

#### Scenario: The arithmetic does not fit

- **WHEN** the declared generations and task count exceed the declared ceiling
- **THEN** the run refuses before the first call and reports the arithmetic it refused on

#### Scenario: Calls are affordable but time is not

- **WHEN** the declared call count fits but the measured per-call latency puts the run past the wall-clock
  ceiling
- **THEN** the run is refused on the time ceiling, because a call budget alone can still take hours
