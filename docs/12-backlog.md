# Backlog

Everything the project knows it has not done, with the reason each item is open. Consolidated from
the planning records that preceded these pages; each item was re-checked against the current tree,
and the verification is named so a future reader can re-run it.

## Builder — skill target

Four gaps found in review on 2026-11-14 and deliberately left out of the change that found them: the
change's completion criterion was its own spec, and each gap lies outside what that spec requires.
Each needs requirement wording before code. **All four are still open**, verified 2026-11-15.

1. **The published `evals.json` is never security-scanned.** `SkillBuilderService` scans only
   `skill.toSkillMd()` (line 130, `scanText(skillName, skill.toSkillMd())`), yet `SkillPublishable`
   publishes the model-drafted suite beside it — arbitrary model text reaching a published folder
   unscanned. This is a spec hole, not a deviation: the design decision scopes the scan to the
   `SKILL.md` body.
2. **A scenario with zero expectations is run and scores 0/0**, contributing nothing to either pass
   rate. A degenerate suite then blocks the build with "skill lift +0.000", indistinguishable from a
   genuinely useless skill, and a suite where most evals are empty silently gates on the one real
   eval. Only `evals.evals().isEmpty()` is guarded (line 120).
3. **No `--min-lift` CLI flag.** `BuildCommand` has no such option, so the "configured minimum" is
   reachable only by a programmatic `BuildOptions` caller. The studio hardcodes `AGENT_SPEC` plus
   `DEFAULT_MIN_LIFT` too.
4. **`GitPublisher` returns a path inside the temporary work tree that `close()` then deletes**, so a
   `git:` publish reports a `published:` location that no longer exists. Pre-existing for packs, not
   introduced by the `Publishable` generalization — and no test covers a skill over `git:`.

## Builder — generation

- **The `embabel` technique cannot publish a multi-step pack** — an upstream compiler limitation, not
  a builder bug; see [`08-builder.md`](08-builder.md). Synthesizing a `DynamicTypeDef` per undeclared
  output would make the build publish, but `types/` would then hold bare names with no property
  definitions: a weaker pack, not a correct one. Deferred rather than done silently.
- **Single-shot multi-agent reliability is unbounded.** POC 24 found it "sometimes valid". Cycles
  arising when parallel branches merge remain the honest failure mode that the validator and bounded
  reorder repair only *detect and block*.
- **Skill editing at runtime, a skill improvement loop, and a process-level improvement loop** are
  each out of scope for their target, not overlooked.

## Product gaps proven in POCs but never wired

- **Retrieval at scale.** No scorer, curation-aware ranking, or retrieval endpoint exists under
  `prod/embabel-smith-studio`; `/retrieve` and `/scale` render `PocOnlyPane.vue`. The scorer seam and
  POC 18's curation are proven only in `poc/poc-22-unified-ui`.
- **Remote invocation.** No remote-action or MCP controller in `prod/`; `/remote` is a `PocOnlyPane`
  stub. POC 13 and POC 16 hold the evidence.
- **Spawning remote sidecars from the UI** (POC 19-style Dev Services) versus requiring them started
  by hand was left open; the gating logic is identical either way.

Verified 2026-11-15 by listing `prod/embabel-smith-studio/src/main/java/.../rest/` (no
retrieve/scale/remote controller) and reading the three pane components.

## Runtime and storage

- **Multi-process trace writing.** v1 is single-process per host with no locking beyond
  append-and-sync.
- **The runtime does not gate on lifecycle state.** Advisory in v1; a runtime-side gate is its own
  change.
- **The approval queue and loop watermarks are in-memory.** A restart loses them; the on-disk traces
  let the loop rebuild. File-backed persistence is a future change if the UI needs it.
- **Further trace backends** — `OtelTraceStore`, BigQuery, MongoDB — were named out of scope. The
  record-shaped `TraceStore` SPI in the runtime module exists so one drops in without reworking the
  JPA implementation.
- **No automatic migration of existing `~/.smith` file data into Postgres.** The filesystem *import*
  endpoint covers the need deliberately.
- **Accepted security risk, documented not fixed:** the storage settings document holds the database
  password in plaintext at `~/.smith/studio/storage.json` (mode `0600`, local development tool, no
  network exposure).
- **Accepted race:** rebinding storage destroys the old entity-manager factory after the swap, so a
  request mid-transaction on the old binding can fail. Tolerated as a short-transaction dev tool; it
  surfaces as a clear error and is noted in the pane.

## Format and sources

- **Deeper-than-one-level pack nesting in a source** is "a future concern, not a v1 feature".

## Spec selection

- **A coarse two-stage family router** over the library was deferred as a possible future POC; the
  POC 17 design rejected it for the shadowing question specifically.
- **Whole-library behaviour reporting** (the full ~281-spec library) was explicitly deferred by POC
  17's open questions.
- **GPU serving, FP8, and multi-model serving** for the small-LLM selector are out of scope, and the
  FP8 checkpoint is excluded by this host's hardware — see the host-specific caveat in
  [`11-spec-selection-pocs.md`](11-spec-selection-pocs.md).

## Loop

- **Closed — the full builder→runtime loop.** This was listed as the single unchecked task in the
  archived `embabel-agent-builder` set (7.1, *needs a live endpoint*). It is done: `run-loop.sh` runs
  it and the results document records it **GREEN** — `docs/04-poc-run-results.md` §"Full loop (task
  7.1)", building a pack, ingesting it, running it and achieving the goal. The unticked box was a
  bookkeeping artefact of the deleted planning tree. Check: `grep -n 'Full loop (task 7.1)'
  docs/04-poc-run-results.md`.
- **Open — the loop publishes with `push = true`, against the product's commit-never-push rule.**
  `ImprovementLoop` and `LoopService` force pushing so a `git:` source's commit survives its
  publisher's throwaway clone, which means an unattended loop writes to the shared remote by
  default. `05-architecture.md` and `06-spec-sources.md` state the opposite as the invariant. Decide
  either to gate it behind configuration or to amend the stated rule. Check: `grep -n 'push=true'
  prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/ImprovementLoop.java`.

## Skill evaluator

- **`embabel-smith-skillevaluator` is built but wired to nothing.** It is a full reactor module
  (`prod/pom.xml`) — the Tier 1 static validator, `Tier1Runner` plus schema, license, hygiene,
  version, quality-score, script-lint, Python-syntax, code-risk and security validators — with no
  model call and no consumer: no other product module depends on it, it is absent from the root POM's
  `dependencyManagement`, and until now it had no mention anywhere in `docs/`, `README.md` or
  `.wiki/`. Decide whether to integrate it into the build/publish gate, expose it as a CLI, or mark
  it deliberately dormant. Check: `grep -rn skillevaluator prod/*/pom.xml pom.xml docs/`.
- **Its provenance is partly unattributed.** The module is a port of a Python `tier1` validator, and
  8 of its source files still carry `SPDX-FileCopyrightText: Copyright (c) 2025/2026 NVIDIA
  CORPORATION & AFFILIATES` (50 carry the project author). `NOTICE` names only "Copyright 2026 Ivan
  Rodriguez", with no upstream attribution anywhere in `NOTICE`, `LICENSE` or `README.md`. Resolve
  the attribution before this module ships. Check: `grep -rho 'SPDX-FileCopyrightText:.*'
  prod/embabel-smith-skillevaluator/src | sort | uniq -c`.

## Test coverage

- **Four `prod/` integration tests are endpoint-gated, so the publish, run and loop paths are never
  executed in CI.** `LiveBuildIntegrationTest`, `LiveRunIntegrationTest`, `LiveLoopIntegrationTest`
  and `RestIntegrationTest` are all `@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL")`.
  Without that variable the offline suite passes and those guarantees are simply untested — including
  the embabel generation seam that `08-builder.md` leans on for its "known upstream limit". Nothing
  here records that, so a green CI run reads as full coverage. Check: `grep -rln
  EnabledIfEnvironmentVariable prod/*/src/test`.

## Re-creating a planning change

The planning trees were consolidated into these pages and removed, but the workflow was kept:
`openspec/config.yaml`, `openspec/specs/.gitkeep` and `openspec/changes/archive/.gitkeep` remain, and
`make setup` still installs the OpenSpec CLI. Starting a new change is therefore still valid; the
capability specs it would edit are now these documents.

One piece of tooling knowledge went with the trees and is recorded here because nothing else keeps
it: capability *renames* cannot be expressed as deltas — a delta operates on requirement names and
must not move or rename a capability — so the 2026-11-11 vocabulary rename was applied as direct
edits, with renames ordered before archiving so archiving could not create incomplete capability
files.
