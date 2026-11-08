# skill-sources Specification

## Purpose

Resolving skill-packs from their sources: a local folder or a git repository. This is the shared layer the runtime reads from and the builder publishes into, so both sides agree on what a source is, where packs are found, and how a pack's content is identified.

## Requirements

### Requirement: Source URI grammar
A skill source SHALL be addressable as a URI of the form `folder:<absolute-or-relative-path>` or `git:<local-path-or-remote-url>[@<ref>]`, where `<ref>` is a branch, tag, or commit SHA and defaults to the repository's default branch when omitted.

#### Scenario: Folder URI parses
- **WHEN** the URI `folder:./skill-packs` is given
- **THEN** it resolves as a folder source rooted at that path

#### Scenario: Git URI with ref parses
- **WHEN** the URI `git:https://example.com/org/skills.git@v1.2` is given
- **THEN** it resolves as a git source pinned to ref `v1.2`

#### Scenario: Malformed URI rejected
- **WHEN** a URI uses an unknown scheme (e.g. `svn:...`) or is empty
- **THEN** resolution fails with an error naming the offending URI

### Requirement: Folder source discovers packs
A folder source SHALL discover skill-packs by scanning its root for directories containing a `manifest.yml` that passes the skill-pack structure validation, one level deep at most.

#### Scenario: Packs discovered
- **WHEN** a folder contains three subdirectories each with a valid `manifest.yml`
- **THEN** the source resolves to those three pack roots

#### Scenario: Invalid pack skipped with warning
- **WHEN** a subdirectory has a `manifest.yml` that fails structure validation
- **THEN** it is excluded from the resolved packs and the resolution result records a warning naming the directory and the validation issue

#### Scenario: Empty folder
- **WHEN** a folder source contains no valid packs
- **THEN** it resolves to an empty pack set with a warning, not an error

### Requirement: Git source resolves into a local cache
A git source SHALL materialize the repository in a local cache directory (default under the user's home, overridable), cloning on first use and fetching on subsequent resolutions, and SHALL check out the pinned ref.

#### Scenario: First resolution clones
- **WHEN** a git source is resolved and its cache directory does not exist
- **THEN** the repository is cloned into the cache and the pinned ref is checked out

#### Scenario: Re-resolution fetches, does not re-clone
- **WHEN** a git source is resolved again after a prior successful resolution
- **THEN** the cached clone is fetched and the pinned ref is checked out, without a fresh clone

#### Scenario: Local repo path works without network
- **WHEN** a git source points at a local repository path
- **THEN** it resolves identically to a remote URL, using only local filesystem access

### Requirement: Ref pinning is stable
When a ref is pinned, resolution SHALL return the tree at that ref; when the default branch is used, re-resolution SHALL follow the branch tip.

#### Scenario: Pinned SHA is immutable
- **WHEN** a source is pinned to a commit SHA and the repository gains new commits
- **THEN** re-resolution returns the same tree as before

#### Scenario: Unpinned follows the branch
- **WHEN** a source uses the default branch and the branch gains a commit that adds a pack
- **THEN** re-resolution includes the new pack

### Requirement: Packs carry content hashes
Each resolved pack SHALL carry a content hash computed over its files (manifest, steps, goal, types), stable for identical content and different when any file changes.

#### Scenario: Hash stable for identical content
- **WHEN** the same pack content is resolved twice
- **THEN** both resolutions report the same content hash

#### Scenario: Hash changes with content
- **WHEN** a step file in a pack is edited and the source re-resolved
- **THEN** the pack's content hash differs from the previous resolution

### Requirement: Failure behavior is explicit
Resolution SHALL fail with a clear, actionable error when a source cannot be materialized (unreachable remote, missing folder, unknown ref, non-git directory).

#### Scenario: Unreachable remote
- **WHEN** a git source points at an unreachable remote URL
- **THEN** resolution fails with an error naming the URL and the git failure

#### Scenario: Unknown ref
- **WHEN** a git source pins a ref that does not exist in the repository
- **THEN** resolution fails with an error naming the missing ref
