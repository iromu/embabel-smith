# Proposal

## Why

Deliverable 3 of the product: a standalone builder. POCs 2/3 prove the NL → validated skill-pack pipeline, POC 5/9 prove eval-guided refinement, POC 21 proves install-time security scanning, POC 18 proves lifecycle metadata (content hash + baseline pass-rate). Today all of that lives in POC launchers writing to a hardcoded `target/` dir. This change assembles them into a product builder that takes a description and publishes a validated, scanned, scored skill-pack into a folder or a git repo — the same source kinds the runtime reads (change 02).

## What Changes

- Fill the `prod/embabel-smith-builder` module (skeleton from change 01) with:
  - Build core extracted from POC 2/3 (compile → validate on a throwaway platform → write; the D5 invariant: nothing invalid is ever written).
  - Eval + bounded refinement (POC 5/9): score the built pack, rewrite prompts from failures, re-validate, bounded iteration count.
  - Security gate (POC 21): the built pack is scanned before publish; high-severity findings block publication.
  - Lifecycle metadata (POC 18): the published pack carries its content hash, baseline eval pass-rate, and initial state `incubating`.
  - Publishing to a `folder:` source or a `git:` source (git publish = commit into the repo's working tree; **no push by default**).
  - A CLI: `smith build "<description>" --out <source-uri> [--max-refine <n>]`.
- Model calls remain endpoint-gated; pipeline control (validation, scanning, hashing, publishing) is offline-testable.

## Capabilities

### New Capabilities

- `skill-builder`: the standalone builder — description → validated pack, eval + bounded refinement, security gate, lifecycle metadata, and publication into folder or git sources.

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-builder/` (new code, tests, README).
- Depends on `smith-common`, `embabel-smith-skillsource`, and Embabel starters (skeleton POM from change 01).
- No POC module is modified; `embabel-smith-studio` (changes 05–06) reuses this module's core classes.
