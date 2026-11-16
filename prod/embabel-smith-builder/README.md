# embabel-smith-builder

The standalone builder product: takes a natural-language description and produces a published,
validated capability. Two output targets share the CLI — an executable agent spec (the default) and
a portable Agent Skill (`SKILL.md`) — selected with `--target`; see
[Output targets](#output-targets). The spec build is a deterministic pipeline — generate both
candidates → validate → write, then score both → bounded refine, then recommend, then a static
security gate, then lifecycle metadata — ending in a publish to a `folder:` or `git:` source (local
commit, no push by default). One description is built twice, into a single-agent pack and into a
multi-agent process; both are scored on the same eval tasks and only the better one is published. See
[The two candidates](#the-two-candidates). Generation is
technique-selectable (single-shot by default, Embabel's NL compiler on request) and both techniques
emit the same agent-spec format, so every later stage is identical whichever one is used. Exposes a
picocli `smith build` CLI.

Non-launcher packages are plain classes (no Spring) so the studio product (change 05) can reuse
the `BuilderService` and the pipeline stages directly. The product extracts proven POC logic
(POC 2/3 builder, POC 5 eval, POC 9 refiner, POC 21 security, POC 18 lifecycle, POC 25 skill
generation and its with/without-skill eval) and never depends on a `poc-*` artifact (enforced by the
`prod` enforcer rule).

## Usage

```
smith build "<description>" --out <source-uri> [--target <agent-spec|skill>]
                            [--technique <single-shot|embabel>]
                            [--max-refine <n>] [--overwrite] [--push]
```

- `<description>` (required) — the natural-language description of the spec to build.
- `--out` (required) — source URI to publish to: `folder:<path>` or `git:<url>[@ref]`.
- `--target` — the artifact to produce: `agent-spec` (default, an executable pack) or `skill`
  (a `SKILL.md` validated by the with/without-skill lift; see
  [Output targets](#output-targets)). An unknown value is a usage error (exit **2**).
- `--technique` — how the description is generated: `single-shot` (default) or `embabel`
  (see [Generation techniques](#generation-techniques)). An unknown value is a usage error
  (exit **2**). Applies to the `agent-spec` target.
- `--max-refine` — maximum refinement rounds (default **3**). Applies to the `agent-spec` target.
- `--overwrite` — replace an existing same-named pack or skill in the target instead of colliding.
- `--push` — push the git commit to the remote (default: **local commit only**, no push).

The build report goes to **stdout** and the exit code is **0** when the pack or skill is published. A
blocked build prints the stage and the reasons it stopped to stdout and exits **1**. Usage errors
(missing `--out`, unknown option) exit **2** (picocli default).

Example:

```
smith build "explain how a coffee machine works" --out folder:./agent-specs
```

### Model endpoint (env-driven)

The model endpoint is fully environment-driven — nothing is hardcoded (same convention as the
runtime, see `application.yml`):

| Variable | Purpose |
|---|---|
| `OPENAI_CUSTOM_BASE_URL` | OpenAI-compatible endpoint (LiteLLM, OpenAI, Ollama, ...) |
| `OPENAI_CUSTOM_API_KEY` | API key |
| `OPENAI_CUSTOM_MODELS` | Comma-separated model list exposed to the platform |

Without an endpoint the CLI exits 1 with a clear configuration error naming the missing
variables.

## Output targets

`--target` picks the artifact the build produces. The two targets share the publish, security and
lifecycle stages — so a published skill carries the same collision and no-push-by-default semantics as
a published pack — but differ in what they generate and in what gates them.

| Target | Flag | Produces | Gate |
|---|---|---|---|
| `agent-spec` (**default**) | `--target agent-spec` | The better of two candidates — a single-agent pack or a multi-agent process (see [The two candidates](#the-two-candidates)) — published as `<out>/<name>/`; both candidates stay in the build's output directory. | The **recommended** candidate's deterministic eval score must reach the score gate (default **1.0**, every task passing). |
| `skill` | `--target skill` | An Agent Skill folder: `SKILL.md`, the `evals.json` it was validated against, and the `lifecycle.yml` sidecar. | The **skill lift** — with-skill pass rate minus the no-skill baseline — must strictly exceed `BuildOptions.minLift()` (default **0**, so any positive lift publishes). |

### The skill pipeline

`SkillBuilderService` runs alongside `BuilderService`; the spec pipeline is untouched. A stage that
stops the build names itself in the report and publishes nothing.

1. **Generate** (`SkillGenerator`). One model call turns the description into the skill's description
   and body. The skill *name* is fixed by the description, not by the model, so a rebuild targets the
   same source location — which is what the collision gate detects.
2. **Validate** (`SkillMdValidator`). The Agent Skills frontmatter constraints: name 1–64
   lowercase-alphanumeric-or-hyphen with no leading, trailing or consecutive hyphens; description
   non-empty and at most 1024 characters. Every violation in one skill is reported together, and no
   eval is run for a skill that could not be published.
3. **Draft the suite** (`EvalSource`). The model drafts the `evals.json` scenarios from the
   description, each with verifiable expectations. An empty suite blocks the build — there is nothing
   to validate the skill against.
4. **Run both arms** (`EvalRunner`). Every scenario runs twice through the builder's model layer: once
   with the skill in the system prompt, once without it (the baseline).
5. **Grade and compare** (`SkillGrader`, `Benchmark`). An LLM judge grades each answer against that
   scenario's expectations; the two pass rates and the lift between them are the gate.
6. **Security → lifecycle → publish.** The same stages the spec path uses (`scanText` over the
   `SKILL.md`, a lifecycle stamp whose baseline is the with-skill pass rate and whose initial state is
   `incubating`, then the collision-gated publisher).

Because the judge is a model, this gate is deliberately **not** deterministic the way the spec path's
eval is. It is relative — a lift over the same model's own baseline — and a build blocked on it
reports the per-scenario grading for both arms so the decision can be inspected:

```
SKILL BUILD OK: explain-how-a-coffee-machine-brews-a-cup
  skill lift:  +0.500 (with 0.750 vs baseline 0.250)
  security:    SAFE (0 critical, 0 warning) at T1_COMMUNITY
  lifecycle:   INCUBATING (hash 9f2c1a…, baseline 0.750)
  published:   /path/to/skills/explain-how-a-coffee-machine-brews-a-cup
```

```
SKILL BUILD BLOCKED at eval (receive-chilled-goods-into-the-cold-store)
  skill lift:  +0.000 (with 1.000 vs baseline 1.000)
  - skill lift +0.000 does not exceed the required +0.000 (with-skill 1.000 vs no-skill baseline 1.000)
  - eval 1 did not improve with the skill: with 2/2 vs without 2/2
  eval 1 "receive a chilled pallet": with 2/2 vs without 2/2
```

A skill is a description that improves a general agent's handling of a process (POC 25 measured
+25% to +58% pass-rate lifts); it does not itself execute one.

```
smith build "receive chilled goods into the cold store and reject a temperature breach" \
            --target skill --out folder:./skills
```

## Generation techniques

`AgentSpecGenerator` is the seam; `GenerationTechnique` names its two implementations, and the
build's `technique` option (or the studio's technique selector) picks one. Both produce the same
shared agent spec, so every stage after generation is identical whichever one ran.

| Technique | Flag | How it generates |
|---|---|---|
| `single-shot` (**default**) | `--technique single-shot` | One model call with the bundled `process_to_flow` prompt returns the whole spec as JSON. The answer is fence-stripped, parsed, and normalized: manifest filled in when absent, goal exported, and prompt vars canonicalized to the names the runtime binds. POC 24 measured this as the stronger technique for the single-pack path, which is why it is the default. |
| `embabel` | `--technique embabel` | Embabel's NL compiler compiles the description into a draft, which `DraftToAgentSpec` maps to the shared format — including the goal-output normalization that keeps the goal's output a returnable type. |

Both call through the same `Ai` model layer, so switching technique never changes the endpoint
configuration. Offline tests substitute a fake `AgentSpecGenerator` (or a canned
`SpecPromptCaller` answer for the single-shot prompt), so the whole pipeline stays verifiable with
no endpoint configured.

### Known limitation of the `embabel` technique

Embabel's NL compiler returns its actions with **no intermediate types** — it omits any type a step
already outputs. `DraftToAgentSpec` maps only the compiler's intermediate types into the pack's
`types/`, so those step and goal output types reach the validator undeclared and the build blocks
at `build` with `DANGLING_TYPE_REF` on every one of them. POC 24 measured this technique at **0/6
valid** on the live matrix, which is why single-shot is the default.

The technique remains selectable and its output is the shared agent-spec format, but a build with
`--technique embabel` should be expected to block rather than publish. Synthesizing a type
declaration for each undeclared output would let such a build publish, but the emitted `types/`
would carry names with no properties, so this is left as a known limitation rather than papered
over. The `single-shot` prompt avoids it by instructing the model to list every non-framework type
in `types`.

## Build pipeline

For the `agent-spec` target, `BuilderService` drives the stages in order; a block at any stage stops
the build and names the stage in the report. One description yields **two** candidates, and the build
publishes the better of them.

1. **Generate → validate → write the single-agent pack** (`BuilderAgent`). The description is
   generated into a draft by the selected technique (see
   [Generation techniques](#generation-techniques)), validated for type bindings and prompt
   references, and — only if valid — written to a scratch directory and re-validated. An invalid
   draft is **never** written (design D5). A draft with generator issues (e.g. an ambiguous
   description) blocks at `build`.
2. **Generate the multi-agent process** (`ProcessSpecGenerator` → `ProcessValidator` →
   `ProcessRepair`). A second model call decomposes the same description into an ordered set of packs
   that hand off to each other, validates its cross-agent hand-offs, and reorders a detected cycle
   within a bound. See [The two candidates](#the-two-candidates).
3. **Score both candidates.** The pack runs the eval task set through the refinement loop
   (`RefinementLoop`): the score is the fraction of passing tasks, and below the gate the terminal
   action's prompt is rewritten and the loop repeats, up to `--max-refine` rounds (a rewrite that
   fails validation is discarded). The process is scored the same way — every pack deployed into one
   scope with the process's entry goal run against the same tasks (`ProcessInvokerFactory`). A
   process that did not validate is not worth a model call and is left unscored.
4. **Recommend** (`CandidateRecommender`). The process is recommended only when it is valid *and*
   strictly out-scores the pack; a tie, an invalid process, or a process the model failed to produce
   recommends the pack.
5. **Score gate**, on the recommended candidate. What must not publish below the gate (default
   **1.0**, i.e. every task passing) is what the build ships — so a strong process is never vetoed by
   a weak pack, and nothing below the gate publishes either way. Both candidates below it → block at
   `eval`.
6. **Store both candidates.** The pack was written at stage 1; the process is written beside it — but
   only if it is valid, because a stored process is one someone could ingest. The rejected candidate
   stays on disk to be inspected; only one is ever published.
7. **Security gate** (`AgentSpecSecurityScanner`). A static scan over the packs the build is about to
   publish — for a process, every one of its packs merged into a single report taking the highest
   tier. A high-severity (critical) finding — e.g. a prompt-injection marker — blocks at `security`.
8. **Lifecycle** (`LifecycleStamper`). The recommended candidate is stamped with a content hash, the
   baseline eval pass-rate, the initial state `incubating` (the `incubating → active` promotion is a
   later transition owned by change 05), and a timestamp, written as a `lifecycle.yml` sidecar next to
   it.
9. **Publish** (`FolderPublisher` / `GitPublisher`). Only the recommended candidate, plus its
   `lifecycle.yml`, is written to the target source and committed (git) or written (folder).

### The two candidates

The same description is sent out twice and the build decides between the results on evidence, not on
a flag — there is no `--multi-agent` option to remember.

| Candidate | Generated by | Scored by | Published when |
|---|---|---|---|
| **Single-agent spec** | the selected generation technique | its pack deployed and run against the eval tasks | it is the only candidate, or it does not lose |
| **Multi-agent process** | one `single-shot` call returning a process JSON (`process_to_flow_multi`) | every pack deployed into one scope, the process's entry goal run against the same eval tasks | it validates **and** strictly out-scores the pack |

The process is the candidate that has to earn its keep, because it is the one that can fail in ways a
lone pack cannot. `ProcessValidator` checks that each agent's exported goal is produced by that
agent's own steps, that every agent is reachable from `UserInput`, that each agent's inputs are fed by
another agent's output (the shared types), that goal names are unique process-wide — and that the
resulting dependency graph has no cycle. Cycles are read over the union of the data edges and the
declared agent order, which keeps the two failure shapes distinct:

- **An ordering mistake** — a producer listed after the agent it feeds — is a cycle the reorder
  settles: `ProcessRepair` topologically reorders the packs and re-validates, bounded at
  `ProcessRepair.MAX_ATTEMPTS`.
- **A genuine mutual dependency** — two agents each needing the other's output — is a cycle in the
  data graph itself. No order settles it, so the process is blocked and the single-agent spec stands
  on its own.

A process that cannot be made sound is named in the report rather than hidden, while the pack goes on
publishing — and its packs are held to the same structural validation a lone pack is, so a process
whose pack consumes an undeclared type loses too. An answer naming fewer than two agents never becomes
a process at all: one pack is the other candidate, so it is refused at generation and reported as a
failed candidate.

```
BUILD OK: receive-chilled-goods
  candidate:   single-agent spec chosen; the multi-agent process 'cold-store' did not validate
               (Circular dependency between agents: market -> risk -> market)
  candidates:  single 1.000, process not scored
  eval score:  1.000
  security:    SAFE (0 critical, 0 warning) at T1_COMMUNITY
  lifecycle:   INCUBATING (hash 9f2c1a…, baseline 1.000)
  published:   /path/to/agent-specs/receive-chilled-goods
```

A published process is a directory holding `process.yml` (the named, ordered pack list) and one pack
directory per agent — which is also a source the runtime ingests directly, since each pack keeps the
layout a standalone pack has.

### Build report

On success:

```
BUILD OK: trading-desk-briefing
  candidate:   multi-agent process out-scored the single-agent spec (1.000 vs 0.500)
  candidates:  single 0.500, process 1.000
  eval score:  1.000
  security:    SAFE (0 critical, 0 warning) at T1_COMMUNITY
  lifecycle:   INCUBATING (hash 9f2c1a…, baseline 1.000)
  published:   /path/to/agent-specs/trading-desk-briefing
```

When blocked:

```
BUILD BLOCKED at security (malicious-spec)
  - INJECTION — prompt-injection marker in action 'say'
```

## Publish semantics

Both targets publish through the same `Publishable` seam — a name, a baseline pass rate and a
`writeTo(Path)` — so the collision gate and the no-push default are shared code rather than a second
copy: `AgentSpecPublishable` writes the pack directory, `ProcessPublishable` the process directory,
`SkillPublishable` the skill directory.

The target is a `folder:` or `git:` source URI (same grammar as the runtime's sources, see
`embabel-smith-specsource`):

- **`folder:<path>`** — the artifact is written to `<path>/<name>/`. A pack carries manifest, goal,
  steps and types; a process carries `process.yml` and one pack directory per agent; a skill carries
  `SKILL.md` and the `evals.json` it was validated against; all carry the `lifecycle.yml` sidecar. A
  same-named artifact already in the folder is a collision unless `--overwrite` replaces it.
- **`git:<url>[@ref]`** — the source is cloned into a scratch work tree, the artifact is written as
  a subdirectory named after it, and committed (author `Smith Builder`). `--push` pushes
  to the remote; without it the commit stays local. A same-named artifact already in the repository
  is a collision unless `--overwrite` replaces it. Re-publishing an unchanged artifact stages
  nothing and skips the commit (the push still delivers the existing commit).

All git operations run non-interactively against the system `git` CLI
(`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).
