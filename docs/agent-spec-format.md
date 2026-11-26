# The agent spec format

A **agent spec** is the on-disk unit of capability in Embabel Smith. It is a *directory*, not a
single file, and it is **pure data** (YAML) — no compiled code, no build step. The builder emits
one; the fixed no-code runtime ingests one with zero compilation.

The format is owned by `smith-common` (`com.embabel.smith.common.model` for the data model,
`com.embabel.smith.common.io` for YAML I/O, `com.embabel.smith.common.validation` for the checks).
The POC modules map this format onto Embabel's runtime types; the format itself stays framework-free.

## Layout

```
<pack>/
  manifest.yml          AgentSpecManifest
  goal.yml              GoalStepSpec   (export: true — the callable goal)
  steps/
    <name>.yml          ActionStepSpec (one file per action)
  types/
    <name>.yml          DynamicTypeDef (intermediate pure-data types)
```

`types/` may be empty (a `.gitkeep` is written to keep the directory). An agent spec exposes
**exactly one** callable goal, and that goal *is* the exported goal.

## The contract

Six invariants define the format. Both sides of the system check against one schema — field names
follow Embabel's own spec YAML (`stepType`, `export`, `inputTypeNames`, `outputTypeName`,
`ownProperties`) — because letting the producer and the consumer parse YAML independently is how the
two drift apart.

1. The directory layout above is complete: `manifest.yml`, `goal.yml`, a non-empty `steps/`, and a
   `types/` directory.
2. The spec is an exported goal — exactly one goal, marked `export: true`, with an output type.
3. Every action step is prompt-backed: it declares its input types, one output type, and a prompt
   that references only names those inputs bind.
4. Intermediate types are pure data — a name, a description, named typed own-properties. No compiled
   JVM class, which is what makes a spec portable and ingestible without a build.
5. Every type reference resolves: framework-provided or declared in `types/`.
6. The manifest declares runtime defaults — `name` and `version` required, planner and model
   optional.

## `manifest.yml` — `AgentSpecManifest`

```yaml
name: explainer
version: 1.0.0
defaultPlanner: GOAP        # optional; per-run planner override still wins
defaultModel: my-model      # optional
```

## `goal.yml` — `GoalStepSpec`

```yaml
stepType: goal
name: explainerComplete
description: "A complete explainer has been produced for the user"
outputTypeName: com.embabel.agent.spec.model.BackgroundMessage
export: true                # REQUIRED — this is what makes the goal callable
```

`export: true` is what makes the goal callable. A goal that is not exported is not callable and is
invalid as an agent spec goal. `outputTypeName` is the type the runtime
returns for a run (the "goal output").

## `steps/<name>.yml` — `ActionStepSpec`

```yaml
stepType: action
name: extractTopic
description: "Extract the topic, audience and tone from the user's request"
inputTypeNames:
  - com.embabel.agent.domain.io.UserInput
outputTypeName: TopicBrief
prompt: |
  From the user request below, extract three things: ...
  User request: {{userInput.content}}
```

Optional, all nullable: `pre`, `post` (lists of condition names), `cost`, `value`, `canRerun`,
`nullable`, `toolGroups`.

### Prompt references

The `prompt` is a Jinja2 template. A reference `{{var}}` or `{{var.prop}}` must be **bound by one
of that action's declared `inputTypeNames`**, where `var` is the decapitalized short name of the
input type:

- input type `UserInput`  → reference `{{userInput}}` or `{{userInput.content}}`
- input type `TopicBrief` → reference `{{topicBrief.topic}}`, `{{topicBrief.audience}}`, ...

If `var.prop` is used and the input type is a declared dynamic type, `prop` must be one of that
type's `ownProperties`. Framework I/O types (`UserInput`, `SystemInput`, `BackgroundMessage`,
`AssistantMessage`) are always available and need not be declared in `types/`.

## `types/<name>.yml` — `DynamicTypeDef`

```yaml
name: TopicBrief
description: "The extracted topic, audience and tone for the explainer"
ownProperties:
  - name: topic
    type: string
    description: "What to explain"
  - name: audience
    type: string
    description: "Who it is for"
parents: []
creationPermitted: true
```

A `DynamicTypeDef` is a **pure-data** intermediate type: a name, a description, and named, typed
own-properties. No compiled JVM class is required — this is what makes an agent spec portable and
ingestible without a build. `type` is a plain scalar (`string`, `integer`, `number`, `boolean`, or
another type name).

## Validation (the group-1 checks)

`AgentSpecValidator` checks, in order:

1. **Layout** — `manifest.yml`, `goal.yml`, a non-empty `steps/`, and a `types/` directory are all
   present.
2. **Exported goal** — exactly one goal, and it is `export: true`.
3. **Prompt references resolve** — every `{{var}}`/`{{var.prop}}` in a step's prompt is bound by one
   of that step's declared input types (and `prop` exists on the declared type when it is a
   dynamic type).
4. **Type references resolve** — every input/output type name used by the goal or any step is either
   framework-provided or declared in `types/`.
5. **A declared input reaches its step's prompt** — a prompt renders as a literal template, so an
   input type the prompt never names is only a blackboard binding: it is never sent to the model, and
   the step answers every distinct request with the same call. The check fires only when *none* of the
   declared inputs is named, so a step that ignores one of several still validates, and a step that
   declares none is out of its scope. A step with no prompt at all is already reported by check 3, so
   it is not reported twice here. This is the guard POC 27's outer loop needed and did not have: its
   `detect` step interpolated no input, so six frozen tasks sent six byte-identical requests and no
   score could separate two candidates ([`04`](04-poc-run-results.md) §7.5).

Issue codes: `LAYOUT_MISSING_MANIFEST`, `LAYOUT_MISSING_GOAL`, `LAYOUT_MISSING_STEPS`,
`LAYOUT_EMPTY_STEPS`, `LAYOUT_MISSING_TYPES`, `GOAL_MISSING`, `GOAL_NOT_EXPORTED`,
`DANGLING_TYPE_REF`, `PROMPT_MISSING`, `PROMPT_UNDECLARED_INPUT`, `PROMPT_UNKNOWN_PROPERTY`,
`PROMPT_IGNORES_DECLARED_INPUT`, `PARSE_ERROR`.

## Mapping to Embabel (the runtime seam)

The format is data; the runtime translates it:

- `DynamicTypeDef` → Embabel `DynamicType` (own-properties → `ValuePropertyDefinition`).
- `ActionStepSpec` / `GoalStepSpec` → Embabel `PromptedActionSpec` / `GoalSpec`, ingested via
  `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()` (no compilation).
- The exported goal's `outputTypeName` is the type the runtime returns for a run.

See `poc-1-no-code-runtime` for the ingestion path and `poc-3-builder-agent` for the emission path.

## Process specs (agents-pack)

An **agents-pack** is a manifest over several agent specs, written in the *process spec* format:
`AgentsPack` is an `AgentsPackManifest` plus a list of `AgentSpec`s. It is written as `process.yml`
beside its member packs, and the reader locates the manifest and then every immediate subdirectory
that carries a `manifest.yml`.

```
<process>/
  process.yml           AgentsPackManifest — keys `process`, `version`, `agents` (member pack names in run order)
  <member-a>/           a complete agent spec, format unchanged
  <member-b>/           a complete agent spec
```

The manifest records the **run order explicitly**. Pack directories sort alphabetically, so an order
inferred from the filesystem would not be the order the model produced — an agents-pack whose members
are named `analysis` and `zeta` would run backwards.

`AgentsPackValidator` checks the hand-offs between members that no single pack can see — input
producer coverage across the whole pack, goal uniqueness across the members, connectivity from
`UserInput` to the final goal, and cycle detection — and it *also* re-checks per-member basics (each
member exports a goal, has steps, and produces its own goal output). An agents-pack that fails is
never stored — a stored one is something someone could ingest. The builder's repair pass is
reorder-only; see [`08-builder.md`](08-builder.md) §Agents-pack output.
