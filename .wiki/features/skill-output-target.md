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
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillScriptHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillGrader.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/Benchmark.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillContentHash.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuildResult.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/LlmJson.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillPrompts.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillFileReferences.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillScript.java"
  - "prod/embabel-smith-builder/src/main/resources/prompts/generate_skill.jinja"
  - "prod/embabel-smith-builder/src/main/resources/prompts/agent_with_skill.jinja"
  - "prod/embabel-smith-builder/src/main/resources/prompts/agent_baseline.jinja"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/SkillPublishable.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/publish/GitPublisher.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SkillRunCommand.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/PublishedSkillHostModelCallTest.java"
  - "docs/13-skill-script-javascript.md"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/BuildController.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/SkillInspectorTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/skill/SkillInspectionGateTest.java"
  - "prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/publish/PublishedSkillScriptToolSurfaceTest.java"
  - "prod/embabel-smith-builder/README.md"
  - "docs/08-builder.md"
  - "docs/12-backlog.md"
updated_at: "2026-11-26"
---

# The skill output target (`--target skill`)

The builder emits a portable **Agent Skill** — a Claude-style `SKILL.md`, not an executable
[[agent-spec-format]] — from the same [[smith-builder]] CLI. The skill is the unit POC 25 measured
([[skill-generation]]); the durable statement of the target's behaviour is `docs/08-builder.md` §`skill`,
and that section is the only contract any of the target's gates has. The capability specs that once carried
the promoted requirements of the lift and `inspect` gates were folded into `docs/` and purged with the rest
of the planning trees, so no `spec.md` names them and nothing outside `docs/` states them; the third static
gate, `references`, arrived with scripts and never had a spec of its own.

## Invocation

```
smith build "<description>" --target skill --out <source-uri>
                            [--max-inspect <n>] [--full-inspection] [--overwrite] [--push]
```

`--target` takes `agent-spec` (default) or `skill`; `BuildTarget.parse` is strict, so a typo exits 2.
`BuildCommand.call()` dispatches on it: `SKILL` goes to `newSkillService()`, otherwise to
`newService(scratch)` — the skill path never builds a `BuilderAgent` and never creates a scratch dir.
`--out`, `--overwrite` and `--push` mean the same on both paths; `--max-refine` is accepted but read only
by the agent-spec path, and `--max-inspect` and `--full-inspection` are the mirror image — read only by
the `inspect` gate below, accepted on an `agent-spec` build and unused there. `--technique` is the
exception to that split: shared code reads it **before** the target dispatch, so
`--flow pack --technique embabel` is refused with exit 1 on a skill build as well
(`cli/BuildCommand.java#call`, ahead of the `BuildTarget.SKILL` dispatch in the same method). `--flow` itself reaches
`BuildOptions` on neither path — the flow is a `BuildCommand` field, not an option the pipeline sees —
so on this target it is accepted and ignored: no pack sits behind a skill build ([[smith-builder]]). The studio's Author pane cannot select this target —
`rest/BuildController.java` hardcodes `BuildTarget.AGENT_SPEC`. Endpoint as usual ([[model-endpoint]]).

## Pipeline

`SkillBuilderService.build(...)`; a failing stage stops the build, names itself, publishes nothing.

1. **Generate** (`SkillGenerator`) — one model call returns `description` + `body` and, where the
   responsibility has a step whose answer is computed rather than judged, a `scripts` list (the prompt half
   is under *Published*); the *name* is `PackName.slug(description)`, not model-supplied, so a rebuild hits
   the same collision gate.
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
   spliced into `agent_with_skill.jinja` at `{skill}`, versus plain `agent_baseline.jinja`. With the script
   engine installed the with-skill call additionally carries a `Skills` reference built from the skill by
   `skill/SkillScriptHost` (`skill/EvalRunner.java:116-118`) — see *The lift gate*.
6. **Grade and compare** (`SkillGrader`, `Benchmark`) — an LLM judge returns
   `{expectations:[{text,passed,evidence}]}`; pass rate is expectations passed / graded, per arm;
   `lift = with − without`.
7. **Security → references → lifecycle → publish** — `AgentSpecSecurityScanner.scanSkill(skillName, texts,
   scriptsByPath)` over **both** model-authored documents the skill publishes — `SKILL.md` *and* the
   rendered `evals.json` — plus every script source (`skill/SkillBuilderService.java:283`); the text-only
   `scanText` is what the pack paths still call. A script is scanned as code rather than as text, so one
   reaching for the host, the network or a path above its own folder blocks the build, and a scriptless
   skill scans to the report the text-only call produced (the generated `lifecycle.yml` is not scanned)
   ([[spec-security-scanning]]). Beside it a second static gate, new with scripts:
   `skill/SkillFileReferences.missing(skill)` compares the paths the body itself claims against the files
   the publisher is about to write and blocks with `SkillBuildResult.blocked("references", …)`
   (`skill/SkillBuilderService.java:304`). It asks Embabel's own `InstructionFileReferenceExtractor`, the
   object the reader's loader validates with, so markdown link targets count and paths inside fenced code
   blocks do not — a body that *teaches* a path is not a body that claims one. It exists because the
   reader's alternative is silent: `DefaultDirectorySkillDefinitionLoader.validateFileReferences` drops the
   **whole skill** over a dangling reference and logs the reason on the reader's side, where the builder
   cannot see it, which is the one failure shape a green report could otherwise hide. Then `LifecycleStamper`
   at `incubating` with the with-skill pass rate as baseline ([[spec-lifecycle]]); the collision-gated
   publisher ([[spec-sources]]).

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

Both arms are prompt substitution by default. With `smith.builder.skill-scripts.enabled` on, the
with-skill arm is additionally offered a tool per script the skill carries, which the baseline arm is
not — so the same threshold is then deciding a different comparison, and a lift taken with the switch on
is not comparable with one taken with it off. See [[skill-scripts-graaljs]] for the engine and
`docs/08-builder.md` §`skill` for how far the switch reaches.

One switch-on build has been measured and its lift went **negative** for a reason the lift did not
measure. `examples/generate-skill.sh Sentinel` at 21:00:08Z with `smith.builder.skill-scripts.enabled=true`
printed `skill lift: -0.125 (with 0.688 vs baseline 0.813)` — two lost checks of sixteen — because the
loader had refused the folder the host wrote: `Skill not found` on all 27 calls the arm made, and one early
`Skipping unloadable skill` WARN (`docs/08-builder.md` §`skill`, `docs/12-backlog.md` §Builder — skill
scripts). The run was blocked at `eval`, so it never reached `publish`:
`examples/out/Sentinel/skill/published/` does not exist, the scratch folder it did write held `SKILL.md`
with no `scripts/` beside it, and
`find examples/out -type d -name scripts` is empty repo-wide after the run as before it. A switch-on lift
against a folder the loader refused measures the refusals — that is, the generator's wording — not the
skill.

## Published

A published skill folder is no longer three files. `<out>/<name>/` still carries `SKILL.md`, `evals.json`
and `lifecycle.yml`, and a scripted skill also writes `scripts/<name>.js` beside them
(`publish/SkillPublishable.java:25,59-68`); a scriptless skill writes exactly the tree it always wrote. The
content hash is SHA-256 over `SKILL.md`, `evals.json` **and the source of every script, in path order**
(`skill/SkillContentHash.java:21-22,32-43`), so a scriptless skill's digest is the string it was before
scripts existed and a script that drifted from the procedure written beside it is precisely the drift the
hash exists to catch. Scripts are not only generator output: the builder's own jar ships one scripted skill,
`src/main/resources/skills/skill-card-generator/`, whose three `.js` files were ported off Python precisely so
the in-JVM engine could be offered them at all (`docs/13-skill-script-javascript.md`,
[[skill-script-javascript]]).

What the model is asked to write is on the mutable side of the prompt split, and it now asks for code:
`prompts/generate_skill.jinja:32-57` tells it to "write a script instead of describing the arithmetic" — one
small script per computed step, none when the responsibility is all judgement, no `fetch`, no XHR, no
sockets — and adds a `scripts: [{path, content}]` member to the JSON contract (`:56`).
`skill/SkillGenerator.java:105-120` parses that list, skipping a blank `content` and deduplicating by path,
and `skill/SkillScript.java:40,60` reduces a declared path to its basename and re-places it under
`scripts/`, so nothing the model names can land outside the folder.

**The script's tool name is not sanitized.** Embabel joins the skill name and the script's basename
verbatim, so a hyphenated skill's script tool is `cold-store-goods-receipt_check-temperature` at the
listing (`publish/PublishedSkillScriptToolSurfaceTest.java:73`, `skill/PublishedSkillHostTest`). That is
the tool's *own* name, not the name a model is offered: a reference-carrying call puts the reference's
sanitized name in front of it, so what reaches the wire is
`smith_published_skills_cold-store-goods-receipt_check-temperature` —
`skill/PublishedSkillHostModelCallTest` asserts the prefixed form in the first request's tool list and the
script's stdout in the follow-up, which is what makes it a dispatch test rather than a listing one. The join
thus has two halves with different hygiene, prefix sanitized and skill segment not, and a body naming the
tool literally is wrong for any hyphenated skill — which is why the shipped instruction points at the path
instead (`prompts/generate_skill.jinja:50,56`). The hazard was
recorded as latent and one live build says otherwise in the *argument* rather than the body: the model
called the annotation tools correctly but in 3 of 27 calls passed `system_specification_md_sentinel_multi_a`
— the sanitized tool-name prefix — as the `skillName`, where the declared name is hyphenated, and those
calls could not resolve however healthy the folder was (`docs/12-backlog.md` §Builder — skill target).

## Gaps

The inspection lineup is hardcoded in `SkillInspector`: `--full-inspection` is the only choice a caller
gets, with no way to request or drop one named check. There is no `--score-gate` flag at all, and the
skill path never reads `scoreGate()`. `GitPublisher` reports a `published:` path inside the work tree its
`close()` deletes. Nothing in `prod/embabel-smith-runtime` consumes a published skill —
`embabel-smith-a2a-starter/…/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill` card entries, which describe a capability
rather than load one ([[a2a-endpoint]]). The consumer that does exist is in the builder, not the runtime:
`skill/PublishedSkillHost` loads the folder `publish` wrote and `smith skill-run` is the one route that
attaches it to a model call ([[smith-builder]]) — one route, not a readership, and this sentence stays true
as written because it names the runtime module. Both stage-7 static gates, the script scan and the `references`
check, sit **after** the eval arms, so a skill that was never going to be publishable has already paid four
model calls per scenario before anything reads what it wrote (`docs/12-backlog.md` §Builder — skill scripts).
That a script tool is *offered* to a model is now proven offline on both routes: the with-skill eval arm's
reference carries it (`skill/EvalRunnerScriptArmTest`), and a published folder's first request carries the
prefixed name and gets the script's stdout back in the follow-up
(`skill/PublishedSkillHostModelCallTest`, [[skill-scripts-graaljs]]). What has never been observed is a live
build emitting a script at all, so the offer has never been seen against a real endpoint.
