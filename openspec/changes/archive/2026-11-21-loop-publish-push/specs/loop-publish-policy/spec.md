# Spec Delta

## Purpose

How the self-improvement loop writes an approved candidate back to its spec source: whether that write
reaches a shared remote, which setting decides it, and what happens to a candidate when the write cannot
be made durable.

## ADDED Requirements

### Requirement: The loop does not push unless it is told to
The improvement loop SHALL push a published candidate to the git remote only when pushing has been
explicitly enabled, and the shipped default SHALL be disabled, so that an unattended loop cannot write
to shared state on its own.

#### Scenario: Default configuration
- **WHEN** the loop publishes a candidate and pushing has not been enabled
- **THEN** the commit is not pushed to the remote

#### Scenario: Pushing enabled
- **WHEN** an operator enables pushing and the loop publishes a candidate to a git source
- **THEN** the commit is pushed and the publish reports that it was

#### Scenario: A scheduled iteration with no operator present
- **WHEN** a scheduled iteration auto-publishes a candidate under the shipped default
- **THEN** nothing reaches the remote

### Requirement: The manual build rule and the loop rule are the same rule
The loop's publish SHALL honour the same commit-never-push rule the manual build honours, and the
architecture and spec-source pages SHALL NOT need an exception for the loop.

#### Scenario: A reader looks up the publishing rule
- **WHEN** someone reads the stated publishing invariant and then reads what the loop does
- **THEN** the loop's behaviour matches it, with no documented exception

### Requirement: A candidate that cannot be written durably is not written at all
When the configured improve source is a git source and pushing is disabled, the loop SHALL NOT attempt
the publish, because the publisher's commit lives only in a temporary clone that is deleted when the
publisher closes.

#### Scenario: Approving with a git source and pushing disabled
- **WHEN** an operator approves a candidate while the improve source is a git source and pushing is
  disabled
- **THEN** the publisher is not called and the approval is refused with a message naming the setting
  that allows it

#### Scenario: The candidate survives the refusal
- **WHEN** an approval is refused for this reason
- **THEN** the candidate remains in the approval queue and can be approved again once pushing is enabled

#### Scenario: Auto-publish meets the same condition
- **WHEN** an auto-publishing iteration runs against a git source with pushing disabled
- **THEN** the iteration records that it published nothing and why, rather than reporting a publish

### Requirement: A local source is unaffected by the setting
The loop SHALL publish to a folder source the same way whether pushing is enabled or not, because a
local write is already durable and the flag is documented as ignored by the folder publisher.

#### Scenario: Folder source with pushing disabled
- **WHEN** an operator approves a candidate while the improve source is a folder source and pushing is
  disabled
- **THEN** the candidate is published and the approval reports the location it was written to

### Requirement: The setting is not switchable while the studio runs
Enabling pushing SHALL require the studio to be configured and started that way, so that no runtime
control can turn on an unattended write to the shared remote.

#### Scenario: Someone asks for a runtime toggle
- **WHEN** a change proposes exposing pushing as a control the loop can flip at runtime
- **THEN** it is refused, and the credential-and-intent reason for the rule is named
