# Proposal

## Why

The no-code runtime can run a pack, but a pack cannot ask for anything beyond prompt text. Its only
capability hook is a step's `toolGroups:` list, and the roles it names reach nothing because no `prod/`
module registers a `ToolGroup` bean — `agent-specs/shell-runner` declares `shell` and, under the shipped
runtime, is offered an empty tool loop. Skills, meanwhile, reach a model only through `smith skill-run`:
[`12-backlog.md`](../../docs/12-backlog.md) §Builder — skill scripts records that "a `smith run` is
offered nothing from a published folder" and names `smith run` as the open reader. A general coding
agent — read a file, search, run a command, consult a skill, all from declarative YAML — is the smallest
worked case that needs both halves closed at once, and it is the case the whole "specs, not agents"
premise was argued for.

The coding agent is an instance, not the claim. The claim is that a pack can follow a skill it has never
seen, which asks four more things of the runtime: that the skill's scripts be executable and controlled
rather than merely readable; that a step be able to name a reference that is not a skill; that whatever
guards a run be stated where it is actually enforced rather than where it would be convenient to write it;
and that the choice of planning strategy be honest, since following an open-ended skill is exactly where a
one-shot goal-directed planner is the wrong instrument. What makes this a design problem rather than a
scripting problem is the format's ceiling: the runtime ingests every subdirectory as a pack and lets the
planner order everything, there is no loop construct, `pre`/`post` are opaque names with no declaration
site, and the upstream reader silently ignores a step key it does not know. A generic pack has to be
expressible inside that, or the format has to grow — and growing it with keys nothing honors is the one
failure mode this change refuses to ship.

## What Changes

- **Tool addons become a thing.** A new module tree ships tools as separately-built libraries rather
  than inside the process that runs them: a parent module with one submodule per capability family, the
  first being `coding` — read file, create/overwrite, targeted edit, list directory, match paths, search
  contents, run a command. The family shape is what keeps later addons (web, task-list, subagent) out of
  the coding module.
- **The runtime answers some tools with nothing configured, and every group has a switch.** The default
  set is drawn from the runtime's own vocabulary: a role the default tier already allows and a class
  exists to back registers at start-up, which today means `file`, read-only until told otherwise. Each
  group then carries a `smith.tools.<group>.enabled` switch in the shape this repo already uses for
  `smith.a2a.enabled` and the Spring Boot starters use for their own optional features, so a default can
  be turned off and a default-off group turned on without a code change, and the run prints which state
  each role arrived in. Presence is still not reach for anything outside the named default set, so a
  platform bump that pulls an addon in transitively cannot widen reach; the default set is pinned by a
  check, as a claim rather than an accident.
- The runtime registers the role names its own trust vocabulary already uses — `file` on by default,
  `shell` behind its switch, since `shell` is not in the default tier's vocabulary at all — so the
  existing pre-run tier check stays the gate on a pack's declaration. A declared role that resolves to
  nothing is now reported by name instead of producing a silent empty tool loop.
- An agent spec may carry Agent Skills in its own `skills/` folder, and a step may declare `references:`
  naming them — the key is Embabel's own step-spec key. A folder holding several agent definitions may
  also carry a shared `skills/` directory that every definition in it can reference; an agent's own copy
  shadows a shared name, and the run says which folder answered.
- The runtime resolves those references during ingest, so a step that names one is offered that skill's
  activation tools on the model call the plan selects. Today the key deserializes and resolves to nothing:
  upstream's scope builder hands the step-spec context an empty reference list.
- The shipped example becomes a folder, `agent-specs/coding-agent`, holding several agent definitions
  with per-agent skills and folder-shared skills, run by `smith run` with nothing configured for its
  read-only definition and one switch for the definition that runs a command.
- A second pack, `agent-specs/arithmetic`, declares nothing but the arithmetic group — the one role an
  untouched runtime already resolves through upstream's own bean — so the declared-group channel has an
  example that needs none of this change, and a broken coding example narrows to the addon rather than to
  the deploy path.
- Script execution moves from out of scope to switched on by opt-in. The engines are the platform's own — a
  host-process engine with a wall-clock limit, an environment allowlist and a `confinedTo(root)` factory,
  plus Docker and Podman engines with their own resource limits — so what is authored here is the property
  that selects one, the default that keeps execution off, and the report line that says which engine
  answered (design D4).
- A generic pack ships beside the coding instance: `agent-specs/skill-interpreter`, a folder of definitions
  that orients on a task, selects and activates a skill, plans against the criteria that skill states, acts
  with tools and scripts, verifies, and escalates when blocked. Its progression is carried by named types,
  because the format has no ordering, no loop and no branch construct (design D12).
- References widen beyond skills. A pack may carry a directory, a document or a brief the way it carries a
  skill folder, and the silent paths around that channel are closed at validation: a dangling name, a name
  pair that sanitizes to one tool prefix, and — new — a step key the format does not implement, because the
  upstream reader ignores unknown keys and an ignored safety key reads as a configured one.
- Guardrails are stated where they actually exist: the platform's global content guardrails registered by
  property, the run's budgets and tool-loop ceiling set by the deployment, and nothing invented as YAML. No
  new step key promises approval, retry or a timeout that no code honors (design D15).
- Human involvement is delivered as a stop that carries the question, the evidence and the command to
  continue. Durable pause and resume stays out, with the prerequisites it would need named rather than
  implied (design D16).
- The planner is documented honestly and measured. The runtime already selects a strategy per run, but
  `SUPERVISOR` routes to the same algorithm as `GOAP`, and no `UTILITY` or `HYBRID` run is recorded anywhere
  in this repo's evidence, so the interpreter pack is run under each and the result written down (design
  D17).

## Capabilities

### New Capabilities

- `tool-addon-modules`: the addon module shape, how a deployment enables one, what start-up reports, that
  presence alone grants nothing, and that the resolvable role set is pinned.
- `coding-tool-addon`: the coding family's working surface — read, write, edit, list, match, search,
  run — confined to one run root, with bounded command execution and bounded search output.
- `agent-spec-skill-references`: the format's skill carriage — an agent's own `skills/`, a folder-shared
  `skills/`, a step's `references:` declaration, the validation that a declared reference resolves, and a
  writer/reader round-trip that does not silently drop it.
- `runtime-skill-hosting`: the runtime resolving declared references at ingest, the per-step offering, load
  refusals reported by name, and the boundary that scripts stay unexecuted while the script switch is off.
- `skill-script-execution`: skill-carried scripts executable through the engines the platform ships, off by
  default, confined to the run root, with the outcome returned as data and every call recorded.
- `skill-interpreter-pack`: the shipped generic pack — a folder of definitions that follows a skill it has
  never seen, chained by types because the format has no flow, asking for capabilities rather than assuming
  them, and reporting what it could not do.
- `pack-carried-references`: references wider than skills — a directory, a document, a brief — declared by
  a pack, with prefix collisions, dangling names and unknown step keys refused at validation instead of
  dropped by the reader.
- `runtime-guardrails`: the guards that actually exist stated at start-up, content guardrails registered by
  configuration, deployment-set bounds, and human involvement delivered as a stop with a question rather
  than a pause the runtime cannot hold.
- `planning-strategy-choice`: the planner named per run from the strategies the platform implements, the
  labels that do not mean what they sound like corrected, and each strategy measured against the
  skill-following pack rather than assumed.
- `coding-agent-example`: the shipped worked example — a folder of agent definitions run by the ordinary
  run command, with its offline-proven and live-only claims kept apart.

### Modified Capabilities

None. `openspec/specs/` is empty — the planning trees were folded into `docs/` and purged — so every
capability here is new.

## Impact

- New modules under `prod/`: a tools parent and its `coding` submodule. `poc/` stays frozen and the
  enforcer's POC ban is untouched; nothing under `prod/` gains a dependency on a POC artifact.
- `prod/embabel-smith-runtime`: ingest gains reference resolution; the CLI and run output gain the
  addon/role report. Whether the runtime declares the coding addon or merely resolves whatever is on the
  classpath is design D2, and either way no runtime class imports an addon class.
- `smith-common`: `ActionStepSpec` gains the reference component the writer must not drop, and
  `AgentSpecValidator` gains the dangling-reference check, the reference-kind and prefix-collision checks,
  and an unknown-step-key check — the last one because the upstream reader drops an unrecognized key without
  a word, which would otherwise let a pack advertise a control it never had. Both sides of the format stay
  one schema.
- Skill scripts widen reach through a door the tier cannot see. A trust tier decides reach by matching
  declared tool-group role names, and a skill's scripts are not a role, so no tier setting blocks a script;
  the gate is the script switch, the chosen engine and the shared root. That asymmetry is stated in the run
  output rather than left to the reader, and it is the reason the default is off.
- `agent-specs/` gains a third folder, `skill-interpreter`, which is data only, and the pack layout gains an
  optional `references.yml` beside the existing files. The runtime reads it; the writer and the studio are
  unchanged until a later change decides they should round-trip it.
- Guardrails and bounds are configuration, not new subsystems: one class implementing the platform's
  guardrail interface, the platform's own properties, and `ProcessOptions` values that `RuntimeCore`
  currently leaves at their defaults. `docs/03-embabel-spec-agent-builder-design.md`'s guardrail row is
  corrected in the same commit, since it lists an annotation that does not exist in these artifacts.
- Planning needs no new seam: `--planner` and a manifest's `defaultPlanner` already reach
  `ProcessOptions`. What lands is a measurement of the interpreter pack under each strategy and a report
  line naming the algorithm that actually planned.
- `prod/embabel-smith-studio` and `prod/embabel-smith-a2a-starter`: touchpoints only — the step shape the
  compose model round-trips, and the card's per-pack tool-group filter, which now has registered groups to
  filter against.
- `agent-specs/`: two added folders, one of which is runnable before the addon exists.
  `embabel-agent-skills` is already managed at 1.5.4, so no version
  change; the coding addon leans on the file tools `embabel-agent-api` already ships rather than
  re-implementing them.
- Reach widens in two places, deliberately and separately. On an untouched deployment a pack that declares
  `file` under the default tier can now read the filesystem — that is what makes the tier's existing
  promise real, and it is why the write surface stays behind its own switch. And with `shell` switched on,
  a pack that declares it under a tier that allows it can run a command. Three states decide reach —
  registered, switched, allowed — and the run says which one it saw, because a silent default and a
  silent refusal look identical from the outside.
