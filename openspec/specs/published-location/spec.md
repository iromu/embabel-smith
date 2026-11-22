# published-location Specification

## Purpose
What a publish may report as the place an artifact now lives, and the requirement that each publishable
artifact shape is exercised against each publish target.

## Requirements

### Requirement: A publish reports a location that outlives the publisher

The location a publish reports SHALL identify where the artifact lives in the source's own terms, and
SHALL remain meaningful after the publisher has released its working storage. A `git:` publish SHALL
NOT report a directory inside the temporary working tree it clones into.

#### Scenario: An artifact is published to a git source

- **WHEN** a pack or a skill is published to a `git:` source and the publisher is closed
- **THEN** the reported location names the repository and the artifact's path within it, and resolves
  to nothing under a temporary directory

#### Scenario: An artifact is published to a folder source

- **WHEN** an artifact is published to a `folder:` source
- **THEN** the reported location is the directory written, as it is today

#### Scenario: The outcome reaches a reader

- **WHEN** a build report or a studio publish response states where an artifact was published
- **THEN** it carries that same location, unchanged from the publish

### Requirement: Each artifact shape is proven against each publish target

A publishable skill SHALL be subject to the same collision, overwrite and commit semantics as a
publishable pack on a git source, and an offline test SHALL cover a skill published to a git source.

#### Scenario: A skill is published into a git source

- **WHEN** a skill is published to a git repository and the commit is read back from a fresh clone
- **THEN** the commit carries the skill directory with its `SKILL.md`, its `evals.json` and the
  lifecycle sidecar

#### Scenario: The name is already taken

- **WHEN** a same-named artifact already exists in the repository and overwrite is not set
- **THEN** the publish is refused as a collision naming the artifact
