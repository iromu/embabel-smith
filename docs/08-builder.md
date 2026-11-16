# The Builder

`prod/embabel-smith-builder` turns a natural-language description into a deployable capability: it
generates a draft, validates it, evaluates it, refines it within a bound, security-scans it, stamps
lifecycle metadata, and publishes it. The output is a *target*: an agent spec, an Agent Skill, or a
multi-agent process.

## It is still an Embabel agent

`core/BuilderAgent` is a code-backed `@Agent` whose actions GOAP-plan over typed pre- and
postconditions: compile → validate → write → evaluate. `BuilderService` is a thin wrapper; the
pipeline order is not hardcoded.

**The actions are code-backed, not LLM-backed.** Downstream steps depend on the typed
`ValidationResult` contract, and letting a model decide the order would introduce exactly the
non-determinism a builder must not have. The split is precise: *pipeline order* is deterministic,
*emitted prompts* are model-generated.

**Validate before write is mandatory.** The generator is model-driven and can emit wrong bindings or
schemas, so the write step may run only after a throwaway-platform deploy has validated them. This
is the guardrail that makes "generate with no code" safe, and it has survived every later builder
change.

## Generation technique

Two techniques, selectable, both feeding the same downstream pipeline:

| Technique | Default | Notes |
| --- | --- | --- |
| `single-shot` | **yes** | one model call, prompt instructs it to declare every non-framework type in `types/` |
| `embabel` | no | Embabel's natural-language compiler |

Single-shot became the default on measured evidence: POC 24 scored the Embabel NL compiler at
**0/6 valid packs against 4/6** for single-shot on the same live matrix. The builder had only the
weaker path.

Two design points a reader of the code will otherwise trip over:

- **The technique seam returns the builder's own `CompiledDraft`** (pack name + `AgentSpec` +
  issues), not an Embabel type. No `AgentCompilationResult` crosses the seam. Keeping the Embabel
  type would have meant an Embabel-only compiler plus a parallel single-shot path — two techniques
  shaped differently, which is the asymmetry the change removed. The draft record also preserves the
  existing issue channel, where an ambiguous description blocks at `build` with the draft retained.
- **An unrecognized technique is refused at the CLI** with a usage error; the studio falls back to
  single-shot rather than failing the request.

### Known upstream limit: the `embabel` technique cannot publish a multi-step pack

Embabel's NL compiler reports a type as *intermediate* only when **no step outputs it**, so for a
normal multi-step agent `getIntermediateTypes()` comes back empty. Every step and goal
`outputTypeName` then reaches `AgentSpecValidator` undeclared and the build blocks with one
`DANGLING_TYPE_REF` per reference — matching POC 24's 0/6.

This is upstream compiler behaviour, not a builder bug, so no amount of retrying or prompt work on
the embabel path fixes it. Synthesizing a `DynamicTypeDef` per undeclared output *would* let the
build publish, but `types/` would then carry bare names with no property definitions — a weaker pack,
not a correct one. That was **deferred rather than done silently**: the technique is verified at the
generation seam only. See [`12-backlog.md`](12-backlog.md).

## Evaluation and refinement

`eval/RefinementLoop` rewrites the prompts implicated in failures, up to a bound.

| Setting | Default |
| --- | --- |
| score gate | `1.0` (`BuildOptions.DEFAULT_SCORE_GATE`) |
| refinement rounds | `3` (`--max-refine`) |

Below the gate after the bound, the build reports and stops — **nothing is published**. The pass-rate
that clears the gate becomes the pack's recorded **baseline**, which the improvement loop compares
future behaviour against.

**The eval invoker runs the pack's own goal.** It originally used
`AgentInvocation.create(platform, BackgroundMessage.class)`, which only matched packs whose goal
output `BackgroundMessage` — but the generation prompt asks the model for the *deliverable* type, so
every single-shot build aborted. It now deploys the pack and `runAgentFrom`s its own goal, the
runtime's path. An unachieved goal yields no output, fails the eval task, and the refinement loop
acts, instead of the build aborting.

## Security scan and lifecycle

`security/AgentSpecSecurityScanner` is the install-time static scan proven in POC 21; a
high-severity finding blocks publication. It shares the honest limit of any non-LLM pattern matcher:
it audits only what the spec *declares*, so a novel rephrasing outside its pattern set can slip
through and a capability smuggled through a bundled script is beyond a static YAML scan.

`lifecycle/LifecycleStamper` records a `lifecycle.yml` **sidecar** — content hash, baseline
pass-rate, state `incubating`. A sidecar rather than a manifest field because the manifest is
format-owned by `smith-common` and shared with the frozen POCs; the sidecar carries the lifecycle
contract without touching the format.

The pack name is a deterministic slug of the description's core noun phrase, so builds are
reproducible.

## Build targets

`--target` selects the output. The gate differs per target, which is why they are parallel services
rather than an overloaded `build`.

### `agent-spec` (default)

The pipeline above, publishing through the grammar in
[`06-spec-sources.md`](06-spec-sources.md).

### `skill`

Generates a portable Agent Skill (`SKILL.md` plus `evals.json`), validated by a **with-skill versus
no-skill eval lift** rather than an absolute score. `SkillMdValidator` enforces the frontmatter
constraints (name 1–64 lowercase alphanumeric-or-hyphen, description ≤ 1024).

**The lift gate is strictly greater than zero.** With a configurable minimum of 0, an *inclusive*
comparison would publish a skill that changed nothing. The default minimum lift is
`BuildOptions.DEFAULT_MIN_LIFT = 0.0`, and the comparison stays strict.

This is a deliberate, documented departure from the spec path's deterministic gate: a relative lift
judged by an LLM is a softer instrument, so the per-scenario report is published alongside the
verdict to make a blocked build inspectable. Both eval arms go through the builder's `Ai` —
re-proving transport independence was POC 25's purpose, not the product's.

### `process`

A build generates **both** candidates — a single-agent spec and a multi-agent process — stores both,
and publishes only the recommended one. `core/CandidateRecommender` prefers the valid candidate with
the higher score; a tie or an invalid process recommends the single-agent spec.

`common/validation/ProcessValidator` checks hand-offs: input-producer coverage, goal uniqueness, and
cycles. `core/ProcessRepair` attempts a bounded reorder repair; a cycle that survives it blocks
*that candidate* — named in the report — while the build continues on the single-agent spec. Repair
is reorder-only, never branch restructuring.

**An unsound process is never stored**, because a stored process is one someone could ingest. Process
evaluation is reimplemented in the builder (the invoker generalized to N packs) rather than depending
on the runtime module.

## CLI

```
smith build "<description>" --out <folder:…|git:…> [--target agent-spec|skill]
            [--technique single-shot|embabel] [--max-refine <n>] [--overwrite] [--push]
```

`cli/SmithBuilderCommand` is the root (`smith`); `cli/BuildCommand` is `build`. As in the runtime,
the CLI wraps `BuilderService` and holds no logic.

## What the builder does *not* do

Skill editing at runtime, a skill self-improvement loop, and a process-level improvement loop are all
out of scope for their respective targets. The studio's process workspace delegates generation here
rather than growing its own generator — see [`09-studio.md`](09-studio.md).
