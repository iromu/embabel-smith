# Proposal

## Why

Three of the four gaps `docs/12-backlog.md` records under *Builder — skill target* are the same
defect wearing three faces: the gate that decides whether a skill publishes is weaker than the
pipeline's own description of it.

- **The scan misses half of what it publishes.** `SkillBuilderService` scans only the `SKILL.md`:
  `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillBuilderService.java:130`
  reads `ScanReport scan = this.scanner.scanText(skillName, skill.toSkillMd());`. Yet
  `SkillPublishable.writeTo` writes the model-drafted suite beside it — line 55
  `Files.writeString(directory.resolve("SKILL.md"), ...)` and line 56
  `Files.writeString(directory.resolve("evals.json"), this.evals.toJson(), StandardCharsets.UTF_8);`.
  So arbitrary model text reaches a published folder unscanned, and a prompt-injection marker in an
  eval expectation is published while the same marker in the body blocks the build.
- **A degenerate suite gates as if it measured something.** `EvalSuite.Eval` defaults a missing
  expectation list to empty (`EvalSuite.java:80`), `EvalRunner` runs every scenario regardless
  (`EvalRunner.java:55` `for (EvalSuite.Eval eval : evals.evals()) {`), and `Benchmark.rate` returns
  `0.0` for a zero total (`Benchmark.java:71` `return (total == 0) ? 0.0 : (double) passed / total;`).
  Only a wholly empty suite is refused — `SkillBuilderService.java:120` `if (evals.evals().isEmpty()) {`.
  A scenario with no expectations therefore spends four model calls to contribute `0/0`, a suite of
  them blocks with "skill lift +0.000" indistinguishable from a genuinely useless skill, and a suite
  where most scenarios are empty silently gates on the one real scenario.
- **The gate's threshold is unreachable from the command line.** `BuildCommand` declares
  `--extract`, `--out`, `--max-refine`, `--overwrite`, `--push`, `--technique` and `--target`
  (`cli/BuildCommand.java:56-81`) and no lift option; line 173-174 constructs the options with
  `BuildOptions.DEFAULT_SCORE_GATE` … `BuildOptions.DEFAULT_MIN_LIFT`. The "configurable minimum"
  `docs/08-builder.md` describes is reachable only by a programmatic `BuildOptions` caller, so the
  person running a skill build cannot ask for a lift that means anything.

## What Changes

- The skill build's static scan covers **both model-authored documents it publishes** — `SKILL.md`
  and `evals.json` — through the scanner's existing text path. The `lifecycle.yml` sidecar stays out
  of the scan: the builder wrote it, the model did not.
- A drafted scenario with **no expectations is refused at the `eval` stage**, naming the scenario
  ids, before either arm of the benchmark runs. Empty scenarios are *not* silently dropped, because
  dropping is what makes a suite gate on an unseen subset.
- `smith build` gains **`--min-lift <n>`**, passed straight to the skill gate, defaulting to the
  existing `BuildOptions.DEFAULT_MIN_LIFT`. It is accepted on an agent-spec build and has no effect
  there, matching how `--max-refine` is already accepted for both targets.
- `docs/08-builder.md` (§`skill` and the CLI block) records the three plus **why the studio has no
  lift knob**, and the closed lines are struck from `docs/12-backlog.md`.

Not touched, and why: the fourth skill-target gap (a `git:` publish reporting a deleted path) is a
publisher concern carried by `builder-publish-location`. And the studio keeps passing
`BuildOptions.DEFAULT_MIN_LIFT` literally (`rest/BuildController.java:65`) with no request field
beside it: `/api/build` pins `rest/BuildController.java:64` to `BuildTarget.AGENT_SPEC`, and the lift
gate applies only to skill builds, so a minimum-lift field on that request would be configurability
nothing can reach. The backlog's item 3 names the studio in the same sentence as the missing flag;
reading the code shows the studio clause is a **symptom of that target pinning, not a second gap**, so
item 3 closes for the CLI and the studio half closes as "not reachable from the studio today". That is
the honest closure, not a narrowing: `design.md` D5 records that the field was ruled in and then out
on this finding. Exposing the technique or the target in the pane stays out of scope — a UI decision
nobody asked for, and this change adds no UI.

## Capabilities

### New Capabilities

- `skill-build-gate`: what the skill target's lift gate is entitled to trust — the documents it
  security-scans before publishing, the minimum validity of the model-drafted eval suite it measures
  against, and who is allowed to set the lift the suite must clear. It is the skill counterpart of
  the agent-spec build's deterministic score gate, and unlike that gate it is judged by a model, so
  its preconditions are what keep the verdict meaningful.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home of these rules is
`docs/08-builder.md`, which already carries the `skill` target and the CLI.

## Impact

- **`prod/embabel-smith-builder`**: `skill/SkillBuilderService` (scan input, suite validation),
  `security/AgentSpecSecurityScanner` (scan the texts it is given rather than one text),
  `cli/BuildCommand` (one option and one argument to `BuildOptions`). No change to `EvalRunner`,
  `Benchmark` or `EvalSuite` — the refusal happens before them.
- **Tests**: additions to `skill/SkillBuilderServiceTest` (a marker in an eval expectation blocks at
  security; a scenario with no expectations blocks at eval and no arm runs) and
  `cli/BuildCommandTest` (the flag reaches the gate; the default does not change).
- **`prod/embabel-smith-studio`**: **no code change**, deliberately. `rest/dto/BuildRequest` stays as
  it is (`description, outputSource, maxRefine, overwrite, push, model` — `dto/BuildRequest.java:21`)
  and `rest/BuildController.java:65` keeps passing `BuildOptions.DEFAULT_MIN_LIFT` literally, because
  line 64 pins `BuildTarget.AGENT_SPEC` and no studio request can reach the skill lift gate. The
  studio half of backlog item 3 closes as *not reachable from the studio today*, not as done.
- **Deliberately omitted**: the technique and target the studio hardcodes
  (`rest/BuildController.java:64`, `GenerationTechnique.SINGLE_SHOT, BuildTarget.AGENT_SPEC`). They
  are a UI decision, not a knob the backlog's item 3 asks to expose — and they are also the reason the
  lift knob has nothing to be exposed *to*.
- **`docs/08-builder.md`**: the `skill` subsection gains the scan-scope and suite rules and the
  no-studio-knob reason; the CLI block gains `--min-lift`.
- **`docs/12-backlog.md`**: items 1 and 2 of *Builder — skill target* move from open to done; item 3
  closes for the CLI with its studio half recorded as *not reachable from the studio today*, not as
  done — that clause is a symptom of the target pinning, not a second gap.
- **Cost**: none. Refusing a degenerate suite *saves* the four model calls per empty scenario it
  would otherwise have run.
