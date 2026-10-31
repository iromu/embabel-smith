---
title: "The smith-common data model"
type: "entity"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/model/SkillPack.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/SkillPackReader.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/SkillPackWriter.java"
  - "smith-common/src/main/java/com/embabel/smith/common/samples/SampleSkillPacks.java"
updated_at: "2026-11-01"
---

# The smith-common data model

`smith-common` is the **format owner** (design D3). It holds the skill-pack data model, the YAML
I/O, and validation — and it is **framework-free** (no Embabel or Spring imports), so it is fully
offline-testable. Both the builder (producer) and the runtime (consumer) depend on it, so the
format has exactly one owner and both sides are checked against the same schema. The on-disk shape
is documented in [[skill-pack-format]].

## The model records (`com.embabel.smith.common.model`)

All are Java `record`s annotated for Jackson YAML (de)serialization:

- **`SkillPack`** — the aggregate: `manifest` + `goal` + `steps` + `types`. This is the
  *in-memory* form of the directory; it is not itself serialized to one YAML file. Convenience
  accessors: `name()`, `version()`, `goalOutputType()` (the exported goal's output type),
  `step(name)`, `type(name)`.
- **`SkillPackManifest`** — `name`, `version`, optional `defaultPlanner`, `defaultModel`.
- **`GoalStepSpec`** — `stepType` (always `"goal"`), `name`, `description`, `outputTypeName`,
  `export`. `isExported()` is `true` only when `export` is `Boolean.TRUE`.
- **`ActionStepSpec`** — `stepType` (always `"action"`), `name`, `description`,
  `inputTypeNames`, `outputTypeName`, `prompt`, plus nullable `pre`, `post`, `cost`, `value`,
  `canRerun`, `nullable`, `toolGroups`.
- **`DynamicTypeDef`** — `name`, `description`, `ownProperties`, `parents`, `creationPermitted`.
  `isCreationPermitted()` defaults to `true`; `hasProperty(name)` checks `ownProperties`.
- **`PropertyDef`** — a named, typed own-property: `name`, `type`, `description`.

A compact constructor in each record normalizes null lists to empty immutable lists, so downstream
code never sees null collections.

## YAML I/O (`com.embabel.smith.common.io`)

- **`SkillPackReader.read(Path)`** — reads `manifest.yml`, `goal.yml`, every `steps/*.yml`, and
  every `types/*.yml` into a `SkillPack`. Non-YAML files (e.g. the `.gitkeep` in an empty
  `types/`) are ignored. Files are read in sorted order for determinism.
- **`SkillPackWriter.write(SkillPack, Path)`** — writes the directory back out, one file per step
  and type. It is **idempotent**: before writing it clears stale files from `steps/` and `types/`,
  so re-writing the same directory never leaves a leftover file (this fixed a `FileAlreadyExists`
  on `types/.gitkeep` across re-writes). If `types/` is empty it still creates the directory with a
  `.gitkeep` so the layout is complete.
- **`Yaml.mapper()`** — the single shared `ObjectMapper` (Jackson YAML). Configured to **omit null
  fields on write** and to **tolerate unknown fields on read**, so the reader can also ingest
  richer Embabel-emitted step files that carry extra fields the pure model doesn't retain.

## Canonical samples (`SampleSkillPacks`)

`SampleSkillPacks` is part of the shared contract, used by `smith-common`'s own tests and by the
POCs as well-formed fixtures, so every module is checked against the same sample:

- `explainer()` — the two-step skill:
  `UserInput → extractTopic → TopicBrief → writeExplainer → BackgroundMessage`. This is the same
  pack as `skill-packs/explainer/` — see [[sample-explainer]].
- `singleStep()` — a one-step skill with **no** intermediate types, used to test the empty
  `types/` round-trip.

The round-trip guarantee (write then read back losslessly) is covered by
`smith-common/src/test/java/com/embabel/smith/common/io/SkillPackRoundTripTest.java`.
