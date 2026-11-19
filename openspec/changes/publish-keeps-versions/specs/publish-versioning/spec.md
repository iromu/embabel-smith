# publish-versioning

## Purpose

Governs what happens to a previously published artifact when something is published under a name the
system already holds. Its rule is that a republish adds rather than replaces, so the self-improvement loop
never deletes the artifact it is comparing against, while a human at the command line can still ask for a
replacement.

## ADDED Requirements

### Requirement: A republished name keeps the artifact it replaced
Publishing an artifact under a name the store already holds SHALL add a version rather than replace what
is there, so the previously published artifact remains readable afterwards.

#### Scenario: The same name is published twice
- **WHEN** an artifact is published under a name that already has a stored version
- **THEN** both versions are readable afterwards and neither content is lost

#### Scenario: The same name and version are published twice
- **WHEN** an artifact is published under a name and version that are already stored together
- **THEN** that version is updated and no second row appears for it

### Requirement: The stored version is the one the artifact declares
The version recorded for a published artifact SHALL be the version the artifact itself declares, and the
store SHALL NOT invent a version number that exists nowhere else in the system.

#### Scenario: A pack is published
- **WHEN** a pack whose manifest declares a version is published
- **THEN** the stored version is that declared version

### Requirement: The improvement loop keeps the version it replaced
The self-improvement loop SHALL store each candidate it publishes as a distinct version of its pack, while
a folder destination SHALL still be replaced in place, and a human at the command line SHALL still be able
to replace a folder by asking for it.

#### Scenario: The loop improves an artifact it already published
- **WHEN** the loop publishes a candidate under a name it published in an earlier iteration
- **THEN** the earlier version is still stored and addressable by name and version, and the loop's next
  comparison has a baseline

#### Scenario: The loop republishes into a folder destination
- **WHEN** the loop publishes a candidate to a folder destination that already holds that pack's files
- **THEN** the folder holds the candidate, and the version it replaced survives in the store rather than on
  disk

#### Scenario: An operator overwrites a folder from the command line
- **WHEN** a publish is asked for without the replace flag and a same-named folder exists
- **THEN** the publish is refused with a message naming the path and the flag

### Requirement: The loop publishes somewhere durable
The loop SHALL NOT publish to a destination that is discarded by the operating system, and where no
publish destination is configured it SHALL refuse to publish and name the setting to change.

#### Scenario: Nothing is configured as the improve source
- **WHEN** the loop reaches a candidate it would publish and no destination is configured
- **THEN** it publishes nothing, reports why, and the candidate stays pending

#### Scenario: The improve source is a git repository
- **WHEN** the loop publishes to a git source with pushing enabled
- **THEN** the publish is a commit in that repository and the previous version remains in its history

### Requirement: A pack named alone means its highest version
Where a published artifact is addressed by name alone, the system SHALL resolve that name to the highest
version stored under it, and SHALL NOT resolve it to whichever row a query happens to return.

#### Scenario: A pack is browsed by name
- **WHEN** a reader asks for a published pack by name and several versions exist
- **THEN** it receives the highest of those versions

#### Scenario: The highest version is retired
- **WHEN** a reader asks for a pack by name and its highest version has been retired
- **THEN** it still receives that highest version, because retirement is applied when a pack runs rather
  than when it is looked up
