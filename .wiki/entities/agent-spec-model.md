---
title: "The smith-common data model"
type: "entity"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/model/AgentSpec.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/AgentsPack.java"
  - "smith-common/src/main/java/com/embabel/smith/common/model/AgentsPackManifest.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/AgentSpecReader.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/AgentSpecWriter.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/SpecFolders.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/AgentsPackReader.java"
  - "smith-common/src/main/java/com/embabel/smith/common/io/AgentsPackWriter.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/AgentsPackValidator.java"
  - "smith-common/src/main/java/com/embabel/smith/common/samples/SampleAgentSpecs.java"
updated_at: "2026-11-25"
---

# The smith-common data model

`smith-common` is the **format owner** (design D3). It holds the agent spec data model, the YAML
I/O, and validation — and it is **framework-free** (no Embabel or Spring imports), so it is fully
offline-testable. Both the builder (producer) and the runtime (consumer) depend on it, so the
format has exactly one owner and both sides are checked against the same schema. The on-disk shape
is documented in [[agent-spec-format]].

## The model records (`com.embabel.smith.common.model`)

All are Java `record`s annotated for Jackson YAML (de)serialization:

- **`AgentSpec`** — the aggregate: `manifest` + `goal` + `steps` + `types`. This is the
  *in-memory* form of the directory; it is not itself serialized to one YAML file. Convenience
  accessors: `name()`, `version()`, `goalOutputType()` (the exported goal's output type),
  `step(name)`, `type(name)`.
- **`AgentSpecManifest`** — `name`, `version`, optional `defaultPlanner`, `defaultModel`.
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

- **`AgentSpecReader.read(Path)`** — reads `manifest.yml`, `goal.yml`, every `steps/*.yml`, and
  every `types/*.yml` into a `AgentSpec`. Non-YAML files (e.g. the `.gitkeep` in an empty
  `types/`) are ignored. Files are read in sorted order for determinism.
- **`AgentSpecWriter.write(AgentSpec, Path)`** — writes the directory back out, one file per step
  and type. It is **idempotent**: before writing it clears stale files from `steps/` and `types/`,
  so re-writing the same directory never leaves a leftover file (this fixed a `FileAlreadyExists`
  on `types/.gitkeep` across re-writes). If `types/` is empty it still creates the directory with a
  `.gitkeep` so the layout is complete.
- **`Yaml.mapper()`** — the single shared `ObjectMapper` (Jackson YAML). Configured to **omit null
  fields on write** and to **tolerate unknown fields on read**, so the reader can also ingest
  richer Embabel-emitted step files that carry extra fields the pure model doesn't retain.
- **`SpecFolders.classpath(resourcePath)`** — turns a pack that ships as a classpath directory (e.g.
  `specs/builder-single-spec`) into a listable `Path`, which is what `AgentSpecReader.read(Path)`
  needs: the class directory itself when classes are exploded, a cached temporary copy when they sit
  inside a jar. A native image cannot list a classpath directory, so there the lookup falls back to a
  directory named by `smith.specs.dir` / `SMITH_SPECS_DIR` and then to the `specs/` folder beside the
  running program — in that order, so a run that can list its own classpath never consults either.
  `AgentSpecReader.readResource(String)` is the reader's use of it.

## The agents-pack aggregate (`AgentsPack`)

Added for the builder's second candidate, and still format-only — the composition semantics live in
the runtime ([[spec-composer]]), the build-time use in [[two-candidate-build]].

- **`AgentsPack`** — `manifest` + `agents` (a `List<AgentSpec>` **in run order**). Accessors:
  `process()`, `version()`, `totalSteps()`, `finalOutputType()`, and `withAgents(reordered)` for the
  cycle repair's reordered copy. `AgentsPack.single(name, agent)` wraps one pack.
- **`AgentsPackManifest`** — `process`, `version`, and the agents' **pack names in run order**. The
  ordered names are the point: packs live one per directory and a directory listing is alphabetical,
  so the order that wires the agents together has to be recorded, not inferred.
- **`AgentsPackWriter.write(process, dir)`** — writes `process.yml` (the manifest) plus one
  agent-spec directory per agent, via the shared `AgentSpecWriter`. That layout is what makes a
  published agents-pack directly ingestible: a source scanning for packs finds every agent of it.
- **`AgentsPackReader.read(dir)`** — reads `process.yml` and every immediate subdirectory holding a
  `manifest.yml`, returning the agents in the manifest's run order rather than the listing's.
- **`AgentsPackValidator`** — the cross-agent check no single-pack validator can make (exported goal,
  goal produced by the agent's own steps, connected from `UserInput` to the final goal, every step
  input fed, unique goal names, no cycle), matching type names by **short name**. Its
  `AgentsPackValidationResult` also carries `sharedTypes`, `connected` and the `cycle` the repair has to
  break.

## Canonical samples (`SampleAgentSpecs`)

`SampleAgentSpecs` is part of the shared contract, used by `smith-common`'s own tests and by the
POCs as well-formed fixtures, so every module is checked against the same sample:

- `explainer()` — the two-step spec:
  `UserInput → extractTopic → TopicBrief → writeExplainer → BackgroundMessage`. This is the same
  pack as `agent-specs/explainer/` — see [[sample-explainer]].
- `singleStep()` — a one-step spec with **no** intermediate types, used to test the empty
  `types/` round-trip.

The round-trip guarantee (write then read back losslessly) is covered by
`smith-common/src/test/java/com/embabel/smith/common/io/AgentSpecRoundTripTest.java`.
