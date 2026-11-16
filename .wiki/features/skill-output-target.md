---
title: "The skill output target (--target skill)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/BuildCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/core/BuildTarget.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/BuildOptions.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/Skill.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillGenerator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillMdValidator.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/EvalSource.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/EvalSuite.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/EvalRunner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillGrader.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/Benchmark.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillContentHash.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuildResult.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/LlmJson.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillPrompts.java"
  - "prod/embabel-smith-builder/src/main/resources/prompts/agent_with_skill.jinja"
  - "prod/embabel-smith-builder/src/main/resources/prompts/agent_baseline.jinja"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/SkillPublishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/BuildController.java"
  - "prod/embabel-smith-builder/README.md"
  - "openspec/specs/agent-skill-builder/spec.md"
updated_at: "2026-11-15"
---

# The skill output target (`--target skill`)

The builder emits a portable **Agent Skill** — a Claude-style `SKILL.md`, not an executable
[[agent-spec-format]] — from the same [[smith-builder]] CLI. The skill is the unit POC 25 measured
([[skill-generation]]); `openspec/specs/agent-skill-builder/spec.md` is its requirement source.

## Invocation

```
smith build "<description>" --target skill --out <source-uri> [--overwrite] [--push]
```

`--target` takes `agent-spec` (default) or `skill`; `BuildTarget.parse` is strict, so a typo exits 2.
`BuildCommand.call()` dispatches on it: `SKILL` goes to `newSkillService()`, otherwise to
`newService(scratch)` — the skill path never builds a `BuilderAgent` and never creates a scratch dir.
`--out`, `--overwrite` and `--push` mean the same on both paths; `--technique` and `--max-refine` are
accepted but read only by the agent-spec path. The studio's Author pane cannot select this target —
`rest/BuildController.java` hardcodes `BuildTarget.AGENT_SPEC`. Endpoint as usual ([[model-endpoint]]).

## Pipeline

`SkillBuilderService.build(...)`; a failing stage stops the build, names itself, publishes nothing.

1. **Generate** (`SkillGenerator`) — one model call returns `description` + `body`; the *name* is
   `PackName.slug(description)`, not model-supplied, so a rebuild hits the same collision gate.
   Model JSON is fence-stripped and retried 3× with linear backoff (`LlmJson`).
2. **Validate** (`SkillMdValidator`) — `name` 1–64 chars of `[a-z0-9-]`, no leading/trailing/consecutive
   hyphen; `description` non-empty, ≤ 1024 chars. All violations reported together.
3. **Draft evals** (`EvalSource`) — the model writes the `evals.json` suite (Anthropic skill-creator
   shape, `EvalSuite`); an empty suite blocks at `eval`.
4. **Run both arms** (`EvalRunner`) — each scenario twice through one `SpecPromptCaller`: skill text
   spliced into `agent_with_skill.jinja` at `{skill}`, versus plain `agent_baseline.jinja`.
5. **Grade and compare** (`SkillGrader`, `Benchmark`) — an LLM judge returns
   `{expectations:[{text,passed,evidence}]}`; pass rate is expectations passed / graded, per arm;
   `lift = with − without`.
6. **Security → lifecycle → publish** — `AgentSpecSecurityScanner.scanText` over the rendered
   `SKILL.md` ([[spec-security-scanning]]); `LifecycleStamper` at `incubating` with the with-skill pass
   rate as baseline ([[spec-lifecycle]]); the collision-gated publisher ([[spec-sources]]).

## The lift gate

Block condition is `summary.lift() <= options.minLift()` with `DEFAULT_MIN_LIFT = 0.0` — strictly
positive lift publishes; a tie blocks. Because the judge is non-deterministic the gate is relative, and
`SkillBuildResult.report()` prints both arms plus per-eval counts so a block is inspectable.

## Published

`<out>/<name>/` with `SKILL.md`, `evals.json` and `lifecycle.yml` (`SkillPublishable`). The content hash
is SHA-256 over both files (`SkillContentHash`).

## Gaps

`evals.json` is published unscanned (only `toSkillMd()` is scanned). A zero-expectation scenario scores
0/0 and is indistinguishable from a useless skill. No `--min-lift` flag — `BuildCommand` always passes
`DEFAULT_MIN_LIFT`, as does the studio; there is no `--score-gate` flag at all, and the skill path never
reads `scoreGate()`. `GitPublisher` reports a `published:` path inside the work tree its `close()`
deletes. Nothing in `prod/embabel-smith-runtime` consumes a published skill.
