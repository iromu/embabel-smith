# Spec Delta

## Purpose

The project's record of what its default test run does not prove: the guarantees that rest only on the
excluded live group, kept as the list of tests that actually carry the exclusion tag.

## ADDED Requirements

### Requirement: The default run's coverage limit is stated where the exclusion is

The documentation that explains the excluded live group SHALL also state which product paths are
exercised only by that group, so that a green default test run is not read as evidence about them.

#### Scenario: A reader asks what the default run proves

- **WHEN** someone reads the live-group section after a green default test run
- **THEN** the section names the paths that run only in the live group and says the default run
  evidences none of them

#### Scenario: The exclusion is changed

- **WHEN** a test is added to or removed from the live group
- **THEN** the same section's statement is updated with it, because the section is the single place
  the limit is recorded

### Requirement: The live group's roster names only tests that exist

The list of tests in the live group SHALL be the tests that carry the exclusion tag in the tree, and
SHALL record how to check the list against the tree.

#### Scenario: A listed test is removed from the tree

- **WHEN** a test named in the section no longer exists
- **THEN** the section no longer names it, and the recorded check is what shows the omission

#### Scenario: The roster is checked today

- **WHEN** the recorded check is run against the current tree
- **THEN** it returns the builder's, the runtime's and the studio's live tests and no example suites
