---
title: "The smith builder product (smith build)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuildOptions.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuildResult.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuilderAgent.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AgentSpecGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AgentSpecGenerators.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuildTarget.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/GenerationTechnique.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/AiSpecPromptCaller.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/AiPromptRewriter.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/SmithBuilderApplication.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/eval/RefinementLoop.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/NoDirectModelRequestTest.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/Publisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/README.md"
updated_at: "2026-11-15"
---

# The smith builder product (`smith build`)

`prod/embabel-smith-builder` is the product's builder: it takes a natural-language description and
produces a **published, validated** artifact through a deterministic pipeline. It extracts the proven
POC logic — POC 2/3 (build), POC 5 (eval), POC 9 (refine), POC 21 (security), POC 18 (lifecycle) —
into one pipeline that ends in a publish to a `folder:` or `git:` source. Non-launcher packages are
plain classes (no Spring) so the studio product can reuse `BuilderService` and the stages directly. It
never depends on a `poc-*` artifact ([[product-phase]]).

## The CLI

```
smith build "<description>" --out <source-uri> [--target agent-spec|skill]
                            [--technique <name>] [--max-refine <n>] [--overwrite] [--push]
```

- `<description>` (required) — the natural-language description of the artifact.
- `--out` (required) — source URI to publish to: `folder:<path>` or `git:<url>[@ref]`
  ([[spec-sources]]).
- `--target` — `agent-spec` (default, an executable pack) or `skill` (a `SKILL.md` folder).
  `core/BuildTarget.parse` is **strict**, so an unknown name is a usage error — unlike `--technique`
  below. The skill target is a separate service; see [[skill-output-target]].
- `--technique` — `single-shot` (default) or `embabel`; see the seam below.
- `--max-refine` — max refinement rounds (default **3**).
- `--overwrite` — replace an existing same-named artifact instead of colliding.
- `--push` — push the git commit to the remote (default: **local commit only**).

There is **no `--score-gate` flag**: the gate is a `BuildOptions` field (default **1.0**) that only
programmatic callers of `BuilderService` — the studio — can set. Exit codes: **0** when published
(report to stdout), **1** when blocked (the stage and reasons to stdout), **2** on usage errors. The
model endpoint is env-driven ([[model-endpoint]]).

## The generation seam

Since commit `ec1a788`, the builder — not the studio — owns generation. `BuilderAgent` reaches the
model **only** through an injected `AgentSpecGenerator` (`builder/core/AgentSpecGenerator.java`), and
`core/GenerationTechnique` names the two implementations:

- `SINGLE_SHOT` → `SingleShotAgentSpecGenerator` — one model call emits the whole spec as JSON.
- `EMBABEL` → `EmbabelAgentSpecGenerator` — Embabel's production NL compiler.

`AgentSpecGenerators.production(ai, dataDictionary)` returns the `Map<GenerationTechnique,
AgentSpecGenerator>` both the CLI and the studio wire up, so the studio owns no generator of its own
([[studio-process-workspace]]). POC 24 is the measurement behind the default: the NL compiler cannot
coordinate shared type names across sibling agents, so **single-shot is the default and the right
choice for multi-agent work** ([[generation-approaches]]).

`GenerationTechnique.from()` is **lenient** — an unrecognized name silently becomes `single-shot`
rather than erroring, so a typo in a script changes behaviour without a warning.

## The model layer

Everything in this module that talks to a model does so through Embabel's `Ai` — no class here opens
its own HTTP connection to the endpoint, and a source-level test (`NoDirectModelRequestTest`) fails
the build if one ever imports `java.net.http` ([[model-calls-go-through-ai]]). Two collaborators carry
it:

- `core/AiSpecPromptCaller` — the `SpecPromptCaller` both generation techniques run their prompts
  through. 12 000-token budget, temperature 0.2, and the system prompt sent as a system prompt
  (`PromptRunner.withSystemPrompt`), skipped when blank.
- `eval/AiPromptRewriter` — the `PromptRewriter` stage 3 refines with. Replaces the deleted
  `LlmPromptRewriter`; a blank completion still returns the original prompt unchanged.

Because of that, `BuildCommand` takes only `(platform, ai)` — it no longer receives a base URL, key
and model name, and `SmithBuilderApplication` no longer reads `OPENAI_CUSTOM_*` itself. The endpoint
env still decides whether a build can run at all; it just doesn't name the model
([[model-endpoint]]).

## The pipeline

`BuilderService.build(...)` runs the stages in order; a block at any stage stops the build and names
the stage in the report. Nothing is published when a gate fails.

1. **Compile → validate → write** the single-agent pack (`BuilderAgent`): compiled through the
   injected generator, validated for type bindings and prompt references, and — only if valid —
   written to a scratch dir and re-validated. An invalid draft is **never** written
   ([[validate-before-write]]); compiler issues (e.g. an ambiguous description) block at `build`.
2. **The second candidate**, from the same description: decompose → validate → repair a cycle →
   re-validate ([[two-candidate-build]]).
3. **Eval → bounded refine** (`RefinementLoop`) on the pack; below the gate the terminal action's
   prompt is rewritten and the loop repeats, up to `--max-refine`. A rewrite that fails validation is
   discarded ([[eval-guided-refinement]]).
4. **Score the process** (`ProcessInvokerFactory`): every pack deployed into one scope, the process's
   entry goal run against the same eval tasks.
5. **Recommend** between the candidates — and *then* apply the score gate, to the **recommended**
   candidate only: what must not ship below the gate is what the build ships.
6. **Store both** candidates (`BuilderAgent.writeProcess` refuses an invalid process), then the
   **security gate** (`AgentSpecSecurityScanner`) over the packs about to be published — a process
   contributes all of its packs. A critical finding blocks at `security` ([[spec-security-scanning]]).
7. **Lifecycle** (`LifecycleStamper`) on the recommended candidate: content hash, baseline eval
   pass-rate, initial state `incubating` (promotion to `active` is owned by the studio), and a
   timestamp, as a `lifecycle.yml` sidecar ([[spec-lifecycle]]).
8. **Publish** only the recommended candidate.

## The publish seam

`publish/Publishable` is the content-agnostic unit a publisher takes — `name()`, `baselinePassRate()`,
`writeTo(dir)` — implemented by `AgentSpecPublishable`, `ProcessPublishable` and `SkillPublishable`.
`Publisher.publish(Publishable, overwrite, push)` is the single abstract method (the old 4-arg
`AgentSpec` form is now a `default`), so every artifact shares the collision and no-push semantics
instead of carrying its own publisher.

- **`folder:<path>`** — written to `<path>/<name>/` (for a process: `process.yml` plus one directory
  per pack) with `lifecycle.yml`. A same-named artifact is a collision unless `--overwrite`.
- **`git:<url>[@ref]`** — cloned into a scratch work tree, written as a subdirectory named after the
  artifact, committed (author `Smith Builder`). `--push` pushes; without it the commit stays local.
  Re-publishing an unchanged artifact stages nothing. All git runs non-interactively
  (`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`).

A success report prints `candidate:` (the recommendation's reason — the two scores, plus what stopped
the rejected candidate), `candidates:`, `eval score:`, `security:`, `lifecycle:` and `published:`; a
block names the stage, the score if there was one, and the reasons.

## Tests

Offline tests cover the `BuilderAgent` compile/validate/write, the `RefinementLoop`, the
`AgentSpecSecurityScanner` verdicts, the `LifecycleStamper`, the publishers (collision, no-push), the
dual-candidate path (`DualCandidateBuildTest`, `CandidateRecommenderTest`, `ProcessRepairTest`,
`SingleShotProcessSpecGeneratorTest`) and the model layer on mocks (`AiPromptRewriterTest`,
`AiSpecPromptCallerTest` — the latter proves the system prompt goes out separately and that a blank
one is omitted) plus the `NoDirectModelRequestTest` guard. The live build (which calls the model) is
endpoint-gated; `LiveBuildIntegrationTest` asserts the caller-visible outcome, not wire-level error
text.
