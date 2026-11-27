# Spec Delta

## Purpose

The first tool addon: the file and command capabilities a general coding agent needs, expressed as
tools an agent spec can be given without compiling anything, bounded by a single run root.

## ADDED Requirements

### Requirement: The coding addon offers a coding agent's working surface

The coding addon SHALL provide tools to read a file, create or overwrite a file, apply a targeted edit
to an existing file, list a directory, match paths by pattern, search file contents, and run a command
and report its exit code and output. Where the platform already ships a tool for one of these
operations the addon publishes that one rather than a second implementation of the same act.

#### Scenario: A step is given the file role

- **GIVEN** an addon whose file group is registered and a step that declares it
- **WHEN** the step's model call is assembled
- **THEN** the request lists read, write and edit tools by name

#### Scenario: A step is given the command role only

- **GIVEN** an addon whose command group is registered and a step that declares only the command role
- **WHEN** the step's model call is assembled
- **THEN** the request lists the command tool and no file tool

#### Scenario: Nothing is offered to a step that declares neither

- **WHEN** a step declares no tool groups
- **THEN** the request lists neither file nor command tools

### Requirement: Every coding tool works inside one run root

File, search and command tools SHALL share a single root directory for the run, settable by the
operator and defaulting to the process's own directory. A path that resolves outside that root — by
traversal, by absolute path, or by a link — is refused, and the refusal names the requested path and the
root so the caller can tell confinement from failure.

#### Scenario: A path inside the root

- **WHEN** a tool is asked for a path under the root
- **THEN** the operation succeeds and reports the path it used

#### Scenario: A path that escapes

- **WHEN** a tool is asked for a path above the root
- **THEN** it refuses, names the path and the root, and leaves the run running

#### Scenario: Reads are recorded

- **WHEN** files are read during a run
- **THEN** the addon can report which paths it read, so a reader can tell what the run touched

### Requirement: An edit states what it changed

A write or edit tool SHALL report what it did — the path and whether it created, replaced or
changed part of a file — and a targeted edit that matches nothing, or matches ambiguously where the
addon requires uniqueness, SHALL fail with that reason rather than writing a guess.

#### Scenario: A unique edit

- **GIVEN** a file containing the text to replace exactly once
- **WHEN** the edit tool is called with that text
- **THEN** the file is rewritten and the result says which path changed

#### Scenario: No match

- **WHEN** the edit tool is called with text the file does not contain
- **THEN** nothing is written and the result says the text was not found

#### Scenario: Ambiguous match

- **WHEN** the edit tool is called with text that occurs more than once and the addon requires a unique
  match
- **THEN** nothing is written and the result says the match was ambiguous and how many there were

### Requirement: Command execution is bounded

A command SHALL run with a working directory inside the run root, a wall-clock limit, captured standard
output and standard error, and the exit code reported. A command that outlives the limit is terminated
and reported as timed out. Output that exceeds the addon's size allowance is truncated and the
truncation is stated in the result.

#### Scenario: A command that finishes

- **WHEN** a short command is run
- **THEN** the result carries its exit code and captured output

#### Scenario: A command that hangs

- **WHEN** a command that never exits is run
- **THEN** it is terminated at the limit and reported as timed out rather than hanging the run

#### Scenario: A command that prints a great deal

- **WHEN** a command's output exceeds the allowance
- **THEN** the result is truncated and says that it was

#### Scenario: A working directory outside the root

- **WHEN** a command asks for a working directory above the root
- **THEN** the request is refused and named

### Requirement: Search results are bounded and addressed

A content search or path match SHALL return matches with their paths and enough context to act on
them, capped at a stated maximum, and SHALL say when the cap was reached rather than returning a
shortened list that reads as exhaustive.

#### Scenario: Fewer matches than the cap

- **WHEN** a search has fewer matches than the cap
- **THEN** every match is returned with its path

#### Scenario: More matches than the cap

- **WHEN** a search has more matches than the cap
- **THEN** the result carries the cap and says it was reached
