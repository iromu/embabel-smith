# Proposal

## Why

`openspec/changes/skillevaluator-dormant/` recorded the Tier 1 skill validator as deliberately dormant.
The project owner has reversed that: the evaluator "has to run also when creating skills, to iterate
over them until the inspection passes, it covers some skill writing guidelines that should be part of
the built process". This change is that reversal, narrowed to the one consumer the ruling names.

The guidelines are already written down, in code the product does not read. `SchemaValidator` enforces
the Agent Skills authoring rules — a top-level `# Title` in the body (HIGH), the recommended
`## Instructions`/`## Examples` sections (`util/Constants.java:46`), a 500-line `SKILL.md` ceiling
(`util/Constants.java:100`), and frontmatter `name` consistent with the folder name (HIGH).
`QualityScoreValidator` scores Correctness 35 %, Discoverability 25 %, Reliability 25 %, Efficiency 15 %
against a floor of 70 (`util/Constants.java:177`). None of it runs today: `grep -n skillevaluator
pom.xml` returns nothing, and the skill build's only content gate is the frontmatter validator plus a
prompt-injection scan.

So a skill publishes on lift alone. `SkillBuilderService` generates the draft
(`skill/SkillBuilderService.java:103`), checks the frontmatter (line 107), then spends four model calls
per eval scenario on the benchmark arms (line 124) and blocks only if the lift is too small (line 125).
A skill that is thin, over the line limit, name-inconsistent or missing its heading sails through, pays
for the whole benchmark, and publishes. Worse, the loop that would fix it does not exist: the skill
target has **no refinement path at all** — `eval/RefinementLoop` rewrites an `AgentSpec`'s terminal
action prompt and is used only by the agent-spec target, so a bad skill draft is never shown what was
wrong with it.

## What Changes

- **A new gate between the frontmatter check and the eval arms.** The drafted `Skill` is materialised to
  a temporary skill folder and run through `Tier1Runner`. Inspection is free of model calls, so running
  it *before* the benchmark is what makes it worth having: a draft that fails on authoring grounds never
  pays for the arms.
- **A bounded redraft loop.** Blocking findings — their text plus the `suggestion` each `Finding` already
  carries — are appended to the generation request and the skill is regenerated, up to
  **`--max-inspect`** rounds (default 2). Advisory MEDIUM/LOW findings are reported, never fed back: they
  are the checks the evaluator itself calls a "convention nudge", and chasing them would spend a call per
  nudge.
- **The lineup is every check the library can run without an external tool.** `schema`, `version`,
  `quality`, `unicode`, `lint` and `license` are blocking; **`pii` runs too but is report-only** — a
  legitimate skill naming a contact address must not block a build on a regex, so its findings are
  advisory and labelled as such in the report. `security`, `secrets` and `code-integrity` are **not
  requested at all**: they shell out to SkillSpector, Gitleaks, Bandit and Semgrep, whose absence sets
  `passed = false` through `markScanIncomplete` (`model/ValidationResult.java:275`) and would make the
  build machine's tool inventory decide the verdict. Their absence is **disclosed, not hidden**: the
  report names the three checks that did not run on both the built and the blocked path, and
  `docs/08-builder.md` says in plain words that the product does no secret scanning and no script risk
  analysis of a generated skill. A `--full-inspection` opt-in adds the three for an operator who has
  installed the tools; it is off by default and is the only path on which a third-party install hint can
  reach a user. `security` also stays owned by `AgentSpecSecurityScanner` at stage `security` (line 130),
  which `builder-skill-gate-integrity` — landing first, see D2 — is widening to cover `evals.json` too.
- **`embabel-smith-skillevaluator` gains its first consumer**: a normal Maven dependency from
  `embabel-smith-builder`, plus the root `dependencyManagement` entry it has never had.
- **Docs and attribution.** `docs/05-architecture.md` and `docs/12-backlog.md` lose the dormancy wording;
  the root `NOTICE` gains the upstream NVIDIA SkillEvaluator paragraph and `README.md` gains the module
  entry — both carried over unchanged from `skillevaluator-dormant`, whose verified provenance facts
  (50 project-author SPDX lines, 8 verbatim-copied data files, zero Java files with an NVIDIA line, module
  `NOTICE` already attributing upstream) are adopted here as-is.

Not touched, and why: the agent-spec target (`--max-refine` and `eval/RefinementLoop` stay exactly as
they are); the eval arms, `Benchmark` and the lift gate; and any **default** use of the four external
scanners, whose install hints name third-party release URLs — a supply-chain decision that stays with the
human, reachable only through the off-by-default `--full-inspection` opt-in and recorded in `design.md`
rather than taken here.

## Capabilities

### New Capabilities

- `skill-build-inspection`: what the skill target checks about a drafted skill *before* it pays for the
  benchmark, which of those checks may block, which run but are reported only as advisories, which are
  skipped by default and disclosed by name, what happens when a skill still fails after the last allowed
  redraft, and what the gate is honest about not checking. It is the skill counterpart of the agent-spec
  target's deterministic eval gate, and unlike that gate it costs nothing to run.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home of these rules is
`docs/08-builder.md`, which already documents the skill target's stage list, alongside
`docs/05-architecture.md` for the module's now-reversed status.

## Impact

- **`prod/embabel-smith-builder`**: `skill/SkillBuilderService` (the gate and its loop, a new `inspect`
  stage), `skill/SkillGenerator` (a redraft entry point that takes the previous findings),
  `cli/BuildCommand` (`--max-inspect`, and the off-by-default `--full-inspection`), `BuildOptions` (two
  fields), `SkillBuildResult` (the inspection report on both the built and blocked paths, so a passing
  skill still shows what was checked, what was advisory, and what did not run).
- **New seam in the builder**: one class holding the evaluator call — materialise the in-memory `Skill`
  to a temp folder named after the skill, call `Tier1Runner`, translate the `ValidationResult` list into
  blocking findings, advisory findings and incomplete-scan names. It is the only builder class allowed to
  import `org.iromu.embabel.smith.skillevaluator`.
- **POMs**: `embabel-smith-builder/pom.xml` gains the dependency; the root `pom.xml`
  `dependencyManagement` gains `embabel-smith-skillevaluator` beside the other `prod/` entries. Reactor
  order is already correct — `prod/pom.xml:25` lists the evaluator before the builder at line 27.
- **Dependency direction is legal**: the enforcer ban is `<exclude>org.iromu.embabel.smith:poc-*</exclude>`
  (`prod/pom.xml:46`) and `embabel-smith-skillevaluator` does not match it; "Extract, do not depend" in
  `docs/05-architecture.md` is about `poc-*` artifacts. See `design.md` D1.
- **Version-alignment risk, checked not assumed**: the evaluator pins `snakeyaml` 2.3 in its own POM
  (`prod/embabel-smith-skillevaluator/pom.xml:19`) while the root POM imports the Spring Boot BOM, which
  also manages SnakeYAML. Task 1.3 resolves which version the builder actually resolves and pins it if the
  BOM wins.
- **Cost**: one model call per redraft, bounded by `--max-inspect` (default 2, so at most two extra calls
  per skill build) and zero on a skill that inspects clean the first time. The inspection itself is
  offline CPU work.
- **`docs/08-builder.md`**: the skill stage list gains `inspect`, the new flag, and the honest list of what
  the gate does not check. **`docs/05-architecture.md`** and **`docs/12-backlog.md`**: dormancy wording
  replaced by "the builder's skill target is its consumer". **`NOTICE`**, **`README.md`**: carried over.
- **`.wiki/`**: the vocabulary changes (a new gate stage, a module that is no longer dormant) touch pages
  that cross-link, so the change ends with a wiki re-ingest, lint and re-publish.
