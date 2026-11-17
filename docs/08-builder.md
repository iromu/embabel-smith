# The Builder

`prod/embabel-smith-builder` turns a natural-language description into a deployable capability: it
generates a draft, validates it, evaluates it, refines it within a bound, security-scans it, stamps
lifecycle metadata, and publishes it. The output is a *target*: an agent spec, an Agent Skill, or a
multi-agent process.

## It is still an Embabel agent

`core/BuilderAgent` is a code-backed `@Agent` whose actions have typed pre- and postconditions:
compile → validate → write → evaluate. **`BuilderService.build()` holds that order explicitly** —
it invokes the actions in the sequence the GOAP planner would choose, so the order is deterministic
by construction rather than planned at run time.

**The actions are code-backed, not LLM-backed.** Downstream steps depend on the typed
`ValidationResult` contract, and letting a model decide the order would introduce exactly the
non-determinism a builder must not have. The split is precise: *pipeline order* is deterministic,
*emitted prompts* are model-generated.

**Validate before write is mandatory.** The generator is model-driven and can emit wrong bindings or
schemas, so the write step may run only after the deterministic `AgentSpecValidator` has passed the
draft — an invalid draft returns a blocked result and nothing is written. The throwaway-platform
deploy comes *later*, as the eval. This is the guardrail that makes "generate with no code" safe, and
it has survived every later builder change.

## Ingestion: documents as build input

A business-process document set is a build input, not only something a user interface can read.
`blueprint/` holds the three stages that turn documents into the description a build consumes, and
each is callable on its own:

| Stage | Class | Bound |
| --- | --- | --- |
| load | `blueprint/BusinessProcessDiscovery` | 300K of `.md`/`.markdown`, from a folder or a `git clone --depth 1` bounded to 180 s |
| assemble | `blueprint/BlueprintLoader` | 200K corpus, every document labelled with its path relative to the root |
| extract | `blueprint/ProcessExtractor` | the `business_process_discovery` prompt through `SpecPromptCaller`; the answer is plain text, not a spec to parse |

**These are pre-stages, not pipeline stages.** Each ends in a `String` and stops there, so
`BuilderService.build(String, PublishTarget, BuildOptions)` keeps its signature and the gate pipeline
is untouched — an ingestion step has no gate to belong to, and the studio's UI needs to show documents
and a description as separate steps. The two caps are different values on purpose: 300K is the corpus
the workspace *presents*, 200K is the corpus a generation *runs from*.

The repository clone runs with `GitPublisher`'s non-interactive posture
(`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`), so a private repository fails rather than waits for
credentials on a terminal nobody is reading.

## Generation technique

Two techniques, selectable, both feeding the same downstream pipeline:

| Technique | Default | Notes |
| --- | --- | --- |
| `single-shot` | **yes** | one model call, prompt instructs it to declare every non-framework type in `types/` |
| `embabel` | no | Embabel's natural-language compiler |

Single-shot became the default on measured evidence: POC 24 scored the Embabel NL compiler at
**0/6 valid packs against 4/6** for single-shot on the same live matrix. The builder had only the
weaker path. Read the single-shot figure with the POC's own caveat — the model is non-deterministic,
and a re-run of the same matrix scored single-shot multi **3/3** where the first run scored 1/3. The
stable finding is `embabel` at 0/N across every run, not the exact single-shot count.

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
`DANGLING_TYPE_REF` per reference. Observed directly: the compiler logs `3 actions, 0 intermediate
types` for a three-step agent, followed by seven dangling references. POC 24 scored this technique
0/6, but its README attributes those failures to cross-agent type-name mismatches, an invented entry
type and void goals as well — the dangling types are the dominant cause, not the only one.

This is upstream compiler behaviour, not a builder bug, so no amount of retrying or prompt work on
the embabel path fixes it. Synthesizing a `DynamicTypeDef` per undeclared output *would* let the
build publish, but `types/` would then carry bare names with no property definitions — a weaker pack,
not a correct one. That was **deferred rather than done silently**: the technique is verified at the
generation seam only. See [`12-backlog.md`](12-backlog.md).

## Evaluation and refinement

`eval/RefinementLoop` rewrites **the terminal action's prompt** — the one producing the goal output —
from the failures, one prompt per round, up to a bound. A wrong prompt on a mid-pipeline step is
never rewritten, so a pack can reach the bound without the failing step ever being touched.

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

`security/AgentSpecSecurityScanner` is the install-time static scan proven in POC 21; a `CRITICAL`
finding blocks publication, while `WARN` findings force review without blocking (the scale is
`INFO` / `WARN` / `CRITICAL` — there is no `HIGH`). It shares the honest limit of any non-LLM pattern
matcher:
it audits only what the spec *declares*, so a novel rephrasing outside its pattern set can slip
through and a capability smuggled through a bundled script is beyond a static YAML scan.

`lifecycle/LifecycleStamper` records a `lifecycle.yml` **sidecar** — content hash, baseline
pass-rate, state `incubating`. A sidecar rather than a manifest field because the manifest is
format-owned by `smith-common` and shared with the frozen POCs; the sidecar carries the lifecycle
contract without touching the format.

The pack name is a deterministic slug of the **whole** description — lowercased, non-alphanumerics
collapsed to hyphens, trimmed to 40 characters, falling back to `built-spec`. No noun-phrase
extraction is involved, so builds are reproducible but the name tracks the description's opening.

## Build targets

`--target` selects the *published* artifact, and there are exactly two values: `agent-spec` and
`skill`. `core/BuildTarget` declares nothing else, so `--target process` is a usage error — see the
last subsection. The gate differs per target, which is why they are parallel services rather than an
overloaded `build`.

Every `agent-spec` build also generates and scores a multi-agent process candidate, whatever the
caller asked for. That is a second set of model calls per build; it is not surfaced in `--help`.

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

### Process output — not a target

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
smith build ("<description>" | --docs <path>) --out <folder:…|git:…>
            [--extract] [--target agent-spec|skill]
            [--technique single-shot|embabel] [--max-refine <n>] [--overwrite] [--push]
```

Exactly one of `<description>` and `--docs` is required; naming neither, or naming both, is a usage
error. `--extract` is a modifier on `--docs`: without it the assembled corpus goes to the build as
the description, with it the discovery prompt runs first and the build gets the extracted business-
process description. Extracting costs a model call, which is why it is opt-in.

`cli/SmithBuilderCommand` is the root (`smith`); `cli/BuildCommand` is `build`. As in the runtime,
the CLI wraps `BuilderService` and holds no build logic — what it does hold is the choice of input,
composing the ingestion pre-stages above into the one description the build takes.

## What the builder does *not* do

Skill editing at runtime, a skill self-improvement loop, and a process-level improvement loop are all
out of scope for their respective targets. The studio's process workspace delegates generation here
rather than growing its own generator — see [`09-studio.md`](09-studio.md).
