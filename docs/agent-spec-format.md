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
  references.yml        OPTIONAL — references the pack carries that are not skills,
                        each entry declaring `name`, `kind` and `path`
  skills/               OPTIONAL — Agent Skills the pack carries (pure data, no code)
    <name>/SKILL.md     front matter declares `name` and `description`
```

`types/` may be empty (a `.gitkeep` is written to keep the directory). An agent spec exposes
**exactly one** callable goal, and that goal *is* the exported goal.

`references.yml` is optional and lists what a step's `references:` may name besides skills: a
`directory`, a `document`, a literal `brief`, or a `skills` folder, each under a name. Only those
kinds are accepted — each names a platform reference type, so neither a property nor this file can
nominate a class. Two of them differ in what the model receives: a
`document` contributes its whole text on every call, while a `directory` contributes its description
and notes and offers prefixed tools for reading parts of it, which is the form a prompt meaning "load
only what you need" wants. `AgentSpecValidator` reads the file straight off disk, and
`AgentSpecWriter` does not emit it, which the last check under **Validation** returns to. What
an ingest does with it is narrower than what the format accepts: a deploy answers a declared name
only from the pack's `skills/` folders, so a `directory`, `document` or `brief` entry validates
today and is then said unresolved by the run rather than constructed — the half of the format the
runtime does not implement yet.

`skills/` is optional: a pack that carries none is a complete agent spec, and the directory adds no
requirement elsewhere in the format. When several agent definitions sit side by side in one folder, a
`skills/` directory *beside* those definitions is shared by every agent in that folder. The shared
set is the **immediate parent only**, never a chain of ancestors — the parent is also the source root
when a source points at a folder of packs, so a stray `skills/` higher up would otherwise be global
to every pack under it. Where an agent carries a name the shared folder also carries, the agent's
own copy serves the reference.

## The contract

Nine invariants define the format. Both sides of the system check against one schema — field names
follow Embabel's own spec YAML (`stepType`, `export`, `inputTypeNames`, `outputTypeName`,
`ownProperties`) — because letting the producer and the consumer parse YAML independently is how the
two drift apart.

1. The directory layout above is complete: `manifest.yml`, `goal.yml`, a non-empty `steps/`, and a
   `types/` directory. `skills/` is optional; where it exists, every entry is a skill folder.
2. The spec is an exported goal — exactly one goal, marked `export: true`, with an output type.
3. Every action step is prompt-backed: it declares its input types, one output type, and a prompt
   that references only names those inputs bind.
4. Intermediate types are pure data — a name, a description, named typed own-properties. No compiled
   JVM class, which is what makes a spec portable and ingestible without a build.
5. Every type reference resolves: framework-provided or declared in `types/`.
6. Every declared reference resolves to something the pack carries — a skill under its own `skills/`
   (or the folder sharing it), or an entry its `references.yml` declares.
7. The manifest declares runtime defaults — `name` and `version` required, planner and model
   optional.
8. A reference the pack declares in `references.yml` is of a kind the runtime can construct, its
   backing path is present in the pack, its `scripts` switch appears only on a kind that hands out
   script tools, and its name is still unique once the platform sanitizes it
   into a tool prefix. Two names collapsing to one prefix is a silent overwrite of one reference's
   tools by the other's, and a name resolving to nothing is a silent skip — neither is discoverable
   at run time.
9. A step file carries only keys the format implements. The reader does not fail on a key it does not
   know, so an invented key — a per-step approval, a retry count — reads as a control that was
   configured while doing nothing, which is worse than the control being absent.

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
`nullable`, `toolGroups`, `references`.

### References

`references` is Embabel's own step-spec key, and each entry is the **declared name** of something the
pack carries — a skill under its own `skills/` (or the folder sharing it), or an entry of its
`references.yml`. It is a *request* for that capability,
not a grant of reach: it adds no trust claim of its own, and it is **not** a tool group — the tier
gate still applies to whatever the step is allowed to call. A step that declares none is offered
nothing from the pack's skills, whatever the pack carries.

```yaml
references:             # OPTIONAL — what this step asks to be in play
  - repo-notes
```

A reference the pack carries without a skill folder is declared in `references.yml` and named here by
the same key:

```yaml
# references.yml
references:
  - name: domain-guide
    kind: directory         # skills | directory | document | brief
    path: references
```

A `skills` entry may add `scripts: true`, which asks for the script-bearing shape of the folder it
names — the same skills, under the same reference name, offering their scripts as callable tools.
Absent means `false`, the reading shape: `activate`, `listResources` and `readResource`, and no
script tool. That default is why a pack that wants one step to read a skill and another to run its
script declares the same folder twice, once plain and once with the switch, under two names — legal
under invariant 8 as long as the two names sanitize to different tool prefixes. Asking for scripts on
a `directory`, `document` or `brief` is refused, because those kinds hand out no script tools and the
switch would then read as a capability that was configured while doing nothing.

A `references:` key written in `goal.yml` is inert: upstream's `GoalSpec` carries no such field and a
goal contributes no model call of its own, so the declaration is ignored and the pack still
validates, with `REFERENCES_INERT_ON_GOAL` recorded. Only action steps reference.

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
6. **Skill folders are skills** — `skills/` is optional, and nothing fires when it is absent. Where it
   exists, a folder beneath it that carries no `SKILL.md` is reported *by name* as a skill the pack
   does not carry, rather than skipped. The skill a reference names is the name its `SKILL.md` front
   matter declares; the folder name answers only when the file declares none.
7. **Declared references resolve** — every `references` entry names a skill the pack's own `skills/`
   or the immediate parent's `skills/` carries, or a reference its `references.yml` declares, one
   issue per declaring step. This is the reference
   counterpart of check 4, and it exists because upstream resolves an unmatched name with a silent
   `map.get`/null skip: the name is then offered to no model and the run answers as though the
   capability never existed. The same rule is why a dangling name must be caught here rather than
   discovered at run time.
8. **A declared reference is one the runtime can build** — the kind is one of `skills`, `directory`,
   `document` or `brief`; a path-backed kind has a path, and that path exists in the pack in the shape
   the kind needs (a `document` a file, the others a directory); and a name is usable as a tool prefix
   and unique, once sanitized, among the other declared names and the skill names the pack already
   answers to. A name written on a goal rather than a step is *advisory*: `GoalSpec` has no references
   field and a goal contributes no model call of its own, so the declaration is ignored and the pack
   deploys — the point of saying it is that the author learns it here rather than at run time.
9. **Step keys are keys the format has** — each step file and `goal.yml` is also read as written,
   since the runtime stages them verbatim and the reader ignores what it does not know. A key nothing
   in the format implements is named and refuses the pack. A key the upstream model implements but
   `AgentSpecWriter` never emits (`llm`, `tools`, `export`) is *advisory*: it honors at deploy and is
   lost at the next save. An action-only key written in `goal.yml` is *advisory* too, under its own
   code, for the reason in check 8.
10. **A references file survives a deploy, not a rewrite** — *advisory*, and it fires on every pack
    that carries the file. The writer emits `manifest.yml`, `goal.yml`, `steps/` and `types/` and has
    no representation for `references.yml`, so a studio save or a builder repair ships the pack with
    that file and the references it declares gone. The format deliberately gives the writer no
    passthrough, so the loss is said out loud instead of being prevented.

Issue codes — every one below is a refusal, so it makes the pack undeployable, except the four marked
*advisory*: `LAYOUT_MISSING_MANIFEST`, `LAYOUT_MISSING_GOAL`, `LAYOUT_MISSING_STEPS`,
`LAYOUT_EMPTY_STEPS`, `LAYOUT_MISSING_TYPES`, `SKILL_MISSING_DEFINITION`, `GOAL_MISSING`,
`GOAL_NOT_EXPORTED`, `DANGLING_TYPE_REF`, `DANGLING_REFERENCE`, `PROMPT_MISSING`,
`PROMPT_UNDECLARED_INPUT`, `PROMPT_UNKNOWN_PROPERTY`, `PROMPT_IGNORES_DECLARED_INPUT`,
`REFERENCE_UNKNOWN_KIND`, `REFERENCE_MISSING_PATH`, `REFERENCE_UNUSABLE_NAME`,
`REFERENCE_PREFIX_COLLISION`, `REFERENCE_UNNAMED`, `UNKNOWN_STEP_KEY`, `PARSE_ERROR`; and advisory —
`REFERENCES_INERT_ON_GOAL`, `KEY_INERT_ON_GOAL`, `KEY_NOT_PRESERVED_ON_REWRITE`,
`REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE`.

`isValid()` means *no error finding*, not *no finding*: a pack carrying an advisory code validates and
deploys, and the advisory is where a declaration that will be ignored — or lost at the next rewrite —
is recorded. A report that a pack is valid should therefore still show its advisories.

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
