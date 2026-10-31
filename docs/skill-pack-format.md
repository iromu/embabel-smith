# The skill-pack format

A **skill-pack** is the on-disk unit of capability in Embabel Smith. It is a *directory*, not a
single file, and it is **pure data** (YAML) — no compiled code, no build step. The builder emits
one; the fixed no-code runtime ingests one with zero compilation.

The format is owned by `smith-common` (`com.embabel.smith.common.model` for the data model,
`com.embabel.smith.common.io` for YAML I/O, `com.embabel.smith.common.validation` for the checks).
The POC modules map this format onto Embabel's runtime types; the format itself stays framework-free.

## Layout

```
<pack>/
  manifest.yml          SkillPackManifest
  goal.yml              GoalStepSpec   (export: true — the callable skill)
  steps/
    <name>.yml          ActionStepSpec (one file per action)
  types/
    <name>.yml          DynamicTypeDef (intermediate pure-data types)
```

`types/` may be empty (a `.gitkeep` is written to keep the directory). A skill-pack exposes
**exactly one** callable skill, and that skill *is* the exported goal.

## `manifest.yml` — `SkillPackManifest`

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
export: true                # REQUIRED — this is what makes the goal a callable skill
```

`export: true` is what exposes the goal as a callable skill. A goal that is not exported does not
expose a skill and is invalid as a skill-pack goal. `outputTypeName` is the type the runtime
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
own-properties. No compiled JVM class is required — this is what makes a skill-pack portable and
ingestible without a build. `type` is a plain scalar (`string`, `integer`, `number`, `boolean`, or
another type name).

## Validation (the group-1 checks)

`SkillPackValidator` checks, in order:

1. **Layout** — `manifest.yml`, `goal.yml`, a non-empty `steps/`, and a `types/` directory are all
   present.
2. **Exported goal** — exactly one goal, and it is `export: true`.
3. **Prompt references resolve** — every `{{var}}`/`{{var.prop}}` in a step's prompt is bound by one
   of that step's declared input types (and `prop` exists on the declared type when it is a
   dynamic type).
4. **Type references resolve** — every input/output type name used by the goal or any step is either
   framework-provided or declared in `types/`.

Issue codes: `LAYOUT_MISSING_MANIFEST`, `LAYOUT_MISSING_GOAL`, `LAYOUT_MISSING_STEPS`,
`LAYOUT_EMPTY_STEPS`, `LAYOUT_MISSING_TYPES`, `GOAL_MISSING`, `GOAL_NOT_EXPORTED`,
`DANGLING_TYPE_REF`, `PROMPT_MISSING`, `PROMPT_UNDECLARED_INPUT`, `PROMPT_UNKNOWN_PROPERTY`,
`PARSE_ERROR`.

## Mapping to Embabel (the runtime seam)

The format is data; the runtime translates it:

- `DynamicTypeDef` → Embabel `DynamicType` (own-properties → `ValuePropertyDefinition`).
- `ActionStepSpec` / `GoalStepSpec` → Embabel `PromptedActionSpec` / `GoalSpec`, ingested via
  `YmlStepSpecRepository` + `StepSpecAgentScopeBuilder.deploy()` (no compilation).
- The exported goal's `outputTypeName` is the type the runtime returns for a run.

See `poc-1-no-code-runtime` for the ingestion path and `poc-3-builder-agent` for the emission path.
