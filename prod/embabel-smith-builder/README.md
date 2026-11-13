# embabel-smith-builder

The standalone builder product: takes a natural-language description and produces a published,
validated agent spec. The build is a deterministic pipeline — generate → validate → write, then
eval → bounded refine, then a static security gate, then lifecycle metadata — ending in a publish
to a `folder:` or `git:` source (local commit, no push by default). Generation is
technique-selectable (single-shot by default, Embabel's NL compiler on request) and both techniques
emit the same agent-spec format, so every later stage is identical whichever one is used. Exposes a
picocli `smith build` CLI.

Non-launcher packages are plain classes (no Spring) so the studio product (change 05) can reuse
the `BuilderService` and the pipeline stages directly. The product extracts proven POC logic
(POC 2/3 builder, POC 5 eval, POC 9 refiner, POC 21 security, POC 18 lifecycle) and never
depends on a `poc-*` artifact (enforced by the `prod` enforcer rule).

## Usage

```
smith build "<description>" --out <source-uri> [--technique <single-shot|embabel>]
                            [--max-refine <n>] [--overwrite] [--push]
```

- `<description>` (required) — the natural-language description of the spec to build.
- `--out` (required) — source URI to publish to: `folder:<path>` or `git:<url>[@ref]`.
- `--technique` — how the description is generated: `single-shot` (default) or `embabel`
  (see [Generation techniques](#generation-techniques)). An unknown value is a usage error
  (exit **2**).
- `--max-refine` — maximum refinement rounds (default **3**).
- `--overwrite` — replace an existing same-named pack in the target instead of colliding.
- `--push` — push the git commit to the remote (default: **local commit only**, no push).

The build report goes to **stdout** and the exit code is **0** when the pack is published. A
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

## Build pipeline

The `BuilderService` drives the stages in order; a block at any stage stops the build and names
the stage in the report.

1. **Generate → validate → write** (`BuilderAgent`). The description is generated into a draft by
   the selected technique (see [Generation techniques](#generation-techniques)), validated for
   type bindings and prompt references, and — only if valid —
   written to a scratch directory and re-validated. An invalid draft is **never** written
   (design D5). A draft with generator issues (e.g. an ambiguous description) blocks at `build`.
2. **Eval → bounded refine** (`RefinementLoop`). The pack is deployed and run against the eval
   task set; the score is the fraction of passing tasks. If the score is below the gate
   (default **1.0**, i.e. all tasks must pass) the terminal action's prompt is rewritten and the
   loop repeats, up to `--max-refine` rounds. A rewrite that fails validation is discarded.
   Still below the gate after the bound → block at `eval`.
3. **Security gate** (`AgentSpecSecurityScanner`). A static scan of the pack's prompts; a
   high-severity (critical) finding — e.g. a prompt-injection marker — blocks at `security`.
4. **Lifecycle** (`LifecycleStamper`). The pack is stamped with a content hash, the baseline
   eval pass-rate, the initial state `incubating` (the `incubating → active` promotion is a
   later transition owned by change 05), and a timestamp, written as a `lifecycle.yml`
   sidecar next to the pack.
5. **Publish** (`FolderPublisher` / `GitPublisher`). The pack plus its `lifecycle.yml` is
   written to the target source and committed (git) or written (folder).

### Build report

On success:

```
BUILD OK: explain-how-a-coffee-machine-works
  eval score:  1.000
  security:    0 findings
  lifecycle:   INCUBATING (hash 9f2c1a…, baseline 1.000)
  published:   /path/to/agent-specs/explain-how-a-coffee-machine-works
```

When blocked:

```
BUILD BLOCKED at security (malicious-spec)
  - INJECTION — prompt-injection marker in action 'say'
```

## Generation techniques

`AgentSpecGenerator` is the seam and `GenerationTechnique` names its two implementations; the
build's `technique` option (or the studio's technique selector) picks one. Both produce the same
shared agent spec, so the stages after generation never know which one ran.

| Technique | Flag | How it generates |
|---|---|---|
| `single-shot` (**default**) | `--technique single-shot` | One model call with the bundled `process_to_flow` prompt returns the whole spec as JSON. The answer is fence-stripped, parsed, and normalized — manifest filled in, goal exported, prompt vars canonicalized to the names the runtime binds. POC 24 measured this as the stronger technique for the single-pack path, which is why it is the default. |
| `embabel` | `--technique embabel` | Embabel's NL compiler compiles the description into a draft, which `DraftToAgentSpec` maps to the shared format — including the goal-output normalization that keeps the goal's output a returnable type. |

Both techniques call through the same `Ai` model layer, so switching techniques never changes the
endpoint configuration. Offline tests substitute a fake `AgentSpecGenerator` (or a canned
`SpecPromptCaller` answer), so the pipeline stays verifiable with no endpoint configured.

## Publish semantics

The target is a `folder:` or `git:` source URI (same grammar as the runtime's sources, see
`embabel-smith-specsource`):

- **`folder:<path>`** — the pack is written to `<path>/<pack-name>/` (manifest, goal, steps,
  types, plus the `lifecycle.yml` sidecar). A same-named pack already in the folder is a
  collision unless `--overwrite` replaces it.
- **`git:<url>[@ref]`** — the source is cloned into a scratch work tree, the pack is written as
  a subdirectory named after the pack, and committed (author `Smith Builder`). `--push` pushes
  to the remote; without it the commit stays local. A same-named pack already in the repository
  is a collision unless `--overwrite` replaces it. Re-publishing an unchanged pack stages
  nothing and skips the commit (the push still delivers the existing commit).

All git operations run non-interactively against the system `git` CLI
(`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).
