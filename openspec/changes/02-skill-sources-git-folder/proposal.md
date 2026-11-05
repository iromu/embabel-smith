# Proposal

## Why

Every POC hardcodes its skill-pack location (a single `BUILDER_PACK_DIR` env var). The product runtime must run skills from a git repo or a folder location, and the product builder must publish packs back into the same kind of source — so the self-improvement loop can close. A shared, source-agnostic skill-source layer is the foundation both deliverables need.

## What Changes

- Fill the `prod/embabel-smith-skillsource` module (skeleton from change 01) with:
  - A `SkillSource` abstraction: a source URI (`folder:<path>` or `git:<url-or-path>[@ref]`) resolves to a set of skill-pack roots with origin metadata.
  - `FolderSkillSource`: scans a local directory for skill-packs (directories containing a valid `manifest.yml`).
  - `GitSkillSource`: clones or updates a git repo (local path or remote URL) into a local cache, pinned to a ref (branch, tag, or commit SHA); re-resolving fetches rather than re-cloning.
  - Per-pack content hashing (the POC 18 drift-detection input) computed at resolve time.
- Fully offline-testable: git tests use local bare repos; no network, no model.

## Capabilities

### New Capabilities

- `skill-sources`: resolving skill-packs from folder and git sources — the source URI grammar, discovery, ref pinning, local caching, content hashing, and error behavior.

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-skillsource/` (new code, tests, README).
- No other module is touched; `smith-common`'s `SkillPackValidator` is reused for discovery validation.
