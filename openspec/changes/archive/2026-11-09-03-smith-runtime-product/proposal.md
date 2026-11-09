# Proposal

## Why

Deliverable 1 of the product: a runtime that runs skills from a git repo or a folder location. The POC runtime (POC 1) proves ingest-and-run for a single hardcoded pack; POC 6 proves multi-pack; POC 10 proves trust-tier gating. None of them take a source, and none record what happened — but the self-improvement loop (change 05) needs a trace of every run. This change builds the product runtime on the shared skill-source layer and makes every run emit a trace.

## What Changes

- Fill the `prod/embabel-smith-runtime` module (skeleton from change 01) with:
  - Runtime core extracted from POC 1 (`SkillRunner`) and generalized to multiple packs from a `SkillSource` (POC 6 multi-pack ingest), with POC 10 trust-tier enforcement.
  - **Trace emission**: every run produces a structured trace record — run identity, source ref, per-step execution (step, status, timing, output excerpt), success/failure, and the goal output — appended to a file-based JSONL trace store.
  - A CLI: `smith run --source <uri> --goal <name> --input <text> [--planner <name>] [--trace-dir <dir>]`.
- Model calls remain endpoint-gated; the core is offline-testable (POC discipline).

## Capabilities

### New Capabilities

- `skill-runtime`: the product runtime — running an exported goal over packs resolved from a skill source, multi-pack ingest, planner selection, trust-tier gating, and the per-run trace record + JSONL store.

### Modified Capabilities

(none)

## Impact

- `prod/embabel-smith-runtime/` (new code, tests, README).
- Depends on `smith-common`, `embabel-smith-skillsource`, and Embabel starters (from change 01's skeleton POM).
- No POC module is modified; `SmithStudio` (change 06) will reuse this module's core classes.
