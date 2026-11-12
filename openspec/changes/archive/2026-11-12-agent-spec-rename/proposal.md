# Proposal

## Why

The project's unit of capability has been called a "skill-pack" since the POC phase, but the
artifact is a directory of Embabel **agent step definitions** — `goal.yml` (GoalSpec),
`steps/*.yml` (PromptedActionSpec), `types/*.yml` (DynamicTypeDef) — ingested via
`YmlStepSpecRepository` + `StepSpecAgentScopeBuilder` and planned by GOAP. "Skill" appears
nowhere in Embabel's vocabulary; the name is a misnomer baked into 197 Java files, 13 spec
capabilities, module names, UI labels, and docs. On 2026-11-11 the user ruled that everything
be renamed to match reality: the unit is an **agent spec**.

## What Changes

- **Rename the vocabulary**: "skill-pack" → "agent spec" (the on-disk bundle: goal + actions +
  types + manifest); "skill" → "spec" for library units and sources. Applies to `prod/`,
  `smith-common`, all POC modules, `openspec/specs/`, `.wiki/`, `docs/`, `README.md`,
  `BLOCKERS.md`, the root POM description, and `built-skills/` → `built-specs/`.
- **Rename 4 POC modules** (directory + Maven artifactId + all POM references):
  `poc-4-skill-format` → `poc-4-agent-spec-format`, `poc-6-skill-composition` →
  `poc-6-agent-spec-composition`, `poc-18-skill-lifecycle` → `poc-18-agent-spec-lifecycle`,
  `poc-21-skill-security` → `poc-21-agent-spec-security`.
- **Rename the product source module**: `embabel-smith-skillsource` → `embabel-smith-specsource`
  (directory, artifactId, POM references).
- **Merge the double specs (option A)** — the pre-product `embabel-agent-builder` change left
  POC-era specs that overlap the product-phase specs describing the same implementation:
  - `skill-runtime` → `agent-spec-runtime`, folding in 2 requirements from `skill-pack-runtime`
    (pluggable model endpoint; deterministic plan structure). `skill-pack-runtime` is deleted —
    its other 3 requirements are subsumed by the product spec (see design.md).
  - `skill-builder` → `agent-spec-builder`, folding in 1 requirement from `skill-builder-agent`
    (builder runs as a single agent — verified still true: `BuilderAgent` is an `@Agent`).
    `skill-builder-agent` is deleted — its other 4 requirements are subsumed.
- **Rename the remaining capabilities**: `skill-pack-format` → `agent-spec-format`,
  `skill-sources` → `spec-sources`, `gliner-skill-classification` → `gliner-spec-classification`,
  `small-llm-skill-classification` → `small-llm-spec-classification`.
- **Fix the "discovery" wording** in docs/wiki/UI: there is no runtime skill discovery. The
  three real things are source resolution (spec source), business-docs loading
  (`/api/discover`), and POC-era build-time spec selection (never wired into the product).

**BREAKING** for any external consumer of Java class names, Maven artifactIds, or spec ids
(none known outside this repo). **Not breaking** for the on-disk format: the YAML is already
Embabel's own format, so no data migration — existing agent specs keep working unchanged.

## Capabilities

### New Capabilities

(none — no new behavior is introduced; the renames below are name-only)

### Modified Capabilities

- `agent-spec-runtime` (renamed from `skill-runtime`): gains 2 requirements — *Model endpoint
  is pluggable* and *Deterministic plan structure* — folded in from the retired
  `skill-pack-runtime`.
- `agent-spec-builder` (renamed from `skill-builder`): gains 1 requirement — *Builder runs as a
  single agent* — folded in from the retired `skill-builder-agent`.

The 8 capability renames and 2 deletions are direct edits to `openspec/specs/` recorded as
tasks (the delta format has no capability-rename idiom — `RENAMED` operates on requirement
names only). Only the 3 folded-in requirements are expressed as delta specs.

## Impact

- **Code**: 197 `*Skill*` Java files renamed/reworded across `smith-common`, `prod/` (4
  modules), and `poc/` (23 modules); frontend (`useSourceSkill.ts`, pane labels); CLI strings.
- **Build**: root `pom.xml` (description + `dependencyManagement`), `poc/pom.xml` and
  `prod/pom.xml` (module entries), 4 POC artifactIds + 1 product artifactId; POMs of the two
  POC dependents (poc-15-composer-ui, poc-22-unified-ui). The enforcer ban is pattern-based
  (`poc-*`) and unaffected.
- **Specs**: all 13 capability directories touched (6 renamed, 2 deleted, 5 reworded in place);
  3 requirements added via delta. Resulting inventory: 11 capabilities.
- **Docs**: `.wiki/` (34 pages, published to GitHub Pages), `docs/` (6 files), `README.md`,
  `BLOCKERS.md`, `built-skills/` → `built-specs/`.
- **Not affected**: on-disk agent-spec YAML, the source URI grammar (`folder:`/`git:`), API
  endpoint paths (already neutral: `/api/sources`, `/api/packs`).
