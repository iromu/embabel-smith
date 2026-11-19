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
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillInspector.java"
  - "prod/embabel-smith-skillevaluator/src/main/java/org/iromu/embabel/smith/skillevaluator/Tier1Runner.java"
  - "prod/embabel-smith-skillevaluator/src/main/java/org/iromu/embabel/smith/skillevaluator/util/Constants.java"
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
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/SkillInspectorTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/SkillInspectionGateTest.java"
  - "prod/embabel-smith-builder/README.md"
  - "openspec/specs/agent-skill-builder/spec.md"
updated_at: "2026-11-19"
---

# The skill output target (`--target skill`)

The builder emits a portable **Agent Skill** — a Claude-style `SKILL.md`, not an executable
[[agent-spec-format]] — from the same [[smith-builder]] CLI. The skill is the unit POC 25 measured
([[skill-generation]]); `openspec/specs/agent-skill-builder/spec.md` is its requirement source.

## Invocation

```
smith build "<description>" --target skill --out <source-uri>
                            [--max-inspect <n>] [--full-inspection] [--overwrite] [--push]
```

`--target` takes `agent-spec` (default) or `skill`; `BuildTarget.parse` is strict, so a typo exits 2.
`BuildCommand.call()` dispatches on it: `SKILL` goes to `newSkillService()`, otherwise to
`newService(scratch)` — the skill path never builds a `BuilderAgent` and never creates a scratch dir.
`--out`, `--overwrite` and `--push` mean the same on both paths; `--technique` and `--max-refine` are
accepted but read only by the agent-spec path, and `--max-inspect` and `--full-inspection` are the
mirror image — read only by the `inspect` gate below, accepted on an `agent-spec` build and unused
there. The studio's Author pane cannot select this target —
`rest/BuildController.java` hardcodes `BuildTarget.AGENT_SPEC`. Endpoint as usual ([[model-endpoint]]).

## Pipeline

`SkillBuilderService.build(...)`; a failing stage stops the build, names itself, publishes nothing.

1. **Generate** (`SkillGenerator`) — one model call returns `description` + `body`; the *name* is
   `PackName.slug(description)`, not model-supplied, so a rebuild hits the same collision gate.
   Model JSON is fence-stripped and retried 3× with linear backoff (`LlmJson`).
2. **Validate** (`SkillMdValidator`) — `name` 1–64 chars of `[a-z0-9-]`, no leading/trailing/consecutive
   hyphen; `description` non-empty, ≤ 1024 chars. All violations reported together.
3. **Inspect** (`SkillInspector`) — the skill writing guidelines as a gate, before any eval call is
   paid for. Details below.
4. **Draft evals** (`EvalSource`) — the model writes the `evals.json` suite (Anthropic skill-creator
   shape, `EvalSuite`); an empty suite blocks at `eval`, and so does a suite with a scenario carrying
   no expectations — such a scenario scores 0/0 on both arms and cannot move the lift, so it is
   refused by scenario id *before* either arm is paid for rather than dropped.
5. **Run both arms** (`EvalRunner`) — each scenario twice through one `SpecPromptCaller`: skill text
   spliced into `agent_with_skill.jinja` at `{skill}`, versus plain `agent_baseline.jinja`.
6. **Grade and compare** (`SkillGrader`, `Benchmark`) — an LLM judge returns
   `{expectations:[{text,passed,evidence}]}`; pass rate is expectations passed / graded, per arm;
   `lift = with − without`.
7. **Security → lifecycle → publish** — `AgentSpecSecurityScanner.scanText` over **both** model-authored
   documents the skill publishes: `SKILL.md` *and* the rendered `evals.json` (the generated
   `lifecycle.yml` is not scanned) ([[spec-security-scanning]]); `LifecycleStamper` at `incubating` with
   the with-skill pass rate as baseline ([[spec-lifecycle]]); the collision-gated publisher
   ([[spec-sources]]).

## The `inspect` gate

This gate exists so the writing guidelines are enforced **before any eval call is paid for**: it makes
no model call, while the eval runner spends four per scenario. `skill/SkillInspector` writes the draft
to `<system temp>/<name>/SKILL.md` — the folder name is the skill name, because the schema check
reports a HIGH `name_consistency` finding whenever frontmatter and folder disagree, which would refuse
every build — and runs `embabel-smith-skillevaluator`'s `Tier1Runner` over it. That module is a static,
no-model-call port of a Python `tier1` validator, and this gate is its **only consumer outside the
module**.

- **Default lineup: the seven checks that need no external binary** — `schema`, `version`, `quality`,
  `unicode`, `lint` and `license` block; `pii` runs and is advisory **by check name, not by severity**,
  so a stricter PII heuristic cannot quietly gain blocking authority.
- **`security`, `secrets` and `code-integrity` are not requested**, because each shells out to
  `skillspector` / `gitleaks` / `bandit` / `semgrep` and a missing binary marks its scan failed — that
  would let the tool inventory of the build machine decide whether a build succeeds. So a default skill
  build does **no** secret, security or code-risk scanning, and the report says so on its own line.
  `--full-inspection` swaps the lineup to `Tier1Runner.DEFAULT_CHECKS` (nine) and is the only route that
  requests them. `secrets` is not even a requestable check name — `RECOGNIZED_CHECKS` has no entry for
  it (it is valid for `skip` alone, naming the scan that runs inside `code-integrity`), so asking for it
  yields a blocking `Unknown Tier 1 check(s): secrets`.
- **The floor that blocks is quality, not length.** `Constants.QUALITY_DEFAULT_MIN_SCORE = 70`, failed
  through the validator's gate. The 500-line body ceiling does *not* block: `SchemaValidator`'s
  `line_count` is `Severity.MEDIUM` recorded with `failOnMedium=false`, so a long draft is commented on
  and still published.
- **Redraft is bounded by `--max-inspect`** (default `BuildOptions.DEFAULT_MAX_INSPECT = 2`, so at most
  three drafts). Only blocking findings go back to the model; the last round breaks explicitly, so the
  draft that leaves the loop is the draft that was inspected. A draft still refused blocks at stage
  `inspect` and publishes nothing.
- **Advisory findings are reported, never fed back** — and they are never empty for a generated skill
  (no `LICENSE` file, no author in metadata, quality deductions), so "N advisory notes, none blocking"
  is the healthy state, not silence. A MEDIUM finding on a *passing* check still reaches that list:
  `ValidationResult.addFinding` routes every non-error finding into `getWarnings()`, which is exactly
  what the seam reads there. The asymmetry is the other way: **all** of a *failing* blocking check's
  findings become blocking, so a convention nudge rides into the redraft prompt when its own check also
  failed.
- **Report lines**: `inspect: <n> checks run, quality <score>/100, <n> draft(s)`, then an unconditional
  `not scanned: security, secrets, code-integrity (no secret, security or code-risk scanning; pass
  --full-inspection to request them)`. A build refused earlier, at `generate` or `validate`, prints
  neither line, because no inspection ran.

**It is a writing-quality gate.** Nothing in it looks for injection markers, secrets or risky code; the
builder's security gate remains `AgentSpecSecurityScanner` (step 7, [[spec-security-scanning]]).
`Check:` `grep -n 'BINARY_FREE_CHECKS = '
prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillInspector.java` for the
seven, and `grep -n 'QUALITY_DEFAULT_MIN_SCORE = '
prod/embabel-smith-skillevaluator/src/main/java/org/iromu/embabel/smith/skillevaluator/util/Constants.java`
for the 70.

## The lift gate

Block condition is `summary.lift() <= options.minLift()` with `DEFAULT_MIN_LIFT = 0.0` — strictly
positive lift publishes; a tie blocks, because an *inclusive* comparison at that default would publish a
skill that changed nothing. `--min-lift <n>` sets the minimum; it is a **CLI knob only** — the studio
pins `BuildTarget.AGENT_SPEC`, so no studio request can reach it. Because the judge is non-deterministic
the gate is relative, and `SkillBuildResult.report()` prints both arms plus per-eval counts so a block is
inspectable.

## Published

`<out>/<name>/` with `SKILL.md`, `evals.json` and `lifecycle.yml` (`SkillPublishable`). The content hash
is SHA-256 over both files (`SkillContentHash`).

## Gaps

The inspection lineup is hardcoded in `SkillInspector`: `--full-inspection` is the only choice a caller
gets, with no way to request or drop one named check. There is no `--score-gate` flag at all, and the
skill path never reads `scoreGate()`. `GitPublisher` reports a `published:` path inside the work tree its
`close()` deletes. Nothing in `prod/embabel-smith-runtime` consumes a published skill.
