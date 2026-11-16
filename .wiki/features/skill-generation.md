---
title: "Blueprint to Agent Skills, validated with and without the skill"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-25-skill-generation/README.md"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/SkillDecomposer.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/SkillGenerator.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/SkillMdValidator.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/EvalSource.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/EvalRunner.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/SkillGrader.java"
  - "poc/poc-25-skill-generation/src/main/java/com/embabel/smith/skillgen/Benchmark.java"
  - "poc/poc-25-skill-generation/results"
  - "examples/blueprints"
updated_at: "2026-11-15"
---

# Blueprint → Agent Skills (POC 25)

POC 24 asked which *generator* produces the better Embabel agent spec ([[generation-approaches]]).
POC 25 asks a different question: can the portable unit of capability be an **Agent Skill** — the
Anthropic `SKILL.md` format, not this project's YAML agent spec
([[agent-spec-format]]) — and does loading the right skill measurably improve a general agent on that
slice of a business process?

The acceptance metric is the answer to "is this skill worth its context cost?": run each eval **with
the skill loaded** and **without it**, and compare pass rates.

## The pipeline

Per blueprint, every model-backed step is labelled with the prompt file it uses (under
`src/main/resources/`):

1. **Decompose** — `SkillDecomposer` + `skill_decomposition.jinja`, **one** LLM call, decides *how
   many* skills the process needs and what each owns. The POC's central claim, and the model's
   heuristic judgment rather than a fixed partition.
2. **Generate** — `SkillGenerator` + `generate_skill.jinja` writes one `SKILL.md` per scope
   (frontmatter `name` + `description`, then the body).
3. **Validate** — `SkillMdValidator` enforces the frontmatter contract: `name` is 1–64 characters of
   `[a-z0-9-]` with no leading or trailing hyphen; `description` checked likewise.
4. **Draft evals** — `EvalSource` + `generate_evals.jinja` produces a per-skill `evals.json` in the
   skill-creator format.
5. **Run both arms** — `EvalRunner` runs every eval twice: skill text in the system prompt
   (`agent_with_skill.jinja`) versus the plain baseline (`agent_baseline.jinja`).
6. **Grade** — `SkillGrader` marks each expectation pass/fail — by **LLM judge**, not a deterministic
   check.
7. **Benchmark** — `Benchmark` reduces the two pass rates to a per-skill lift.

Output: `results/<blueprint>/<skill>/SKILL.md` + `evals.json`, and `results/comparison.md`. Run with
`mvn -pl poc/poc-25-skill-generation test` (offline) or `spring-boot:run` (endpoint-gated). All runs
use `Qwen-instruct` at the same temperature, so the delta is the skill's presence, not the model.

## What it measured

Aurora decomposed into **9** stages, Sentinel into **8** specialist functions, TradingAgents into a
**16-role firm** (analysts → research debate → trader → risk debate → portfolio manager →
settlement/learning). Across **27 measured skills** the mean lift was **+16.4%**, positive on 19.

The lift concentrates exactly where it should — on non-obvious domain facts a general model does not
know: **decision-recording-and-reporting +75%** (provenance headers, snapshot-cleanup rules),
**fundamentals-analysis +54%** (point-in-time accuracy), **run-resumption-management +46%**
(snapshot-identity matching).

**Read a small negative lift as redundancy, not damage.** Four skills went slightly backwards
(TradingAgents research-manager-verdict −13%, Sentinel orchestration-and-coordination −10%,
threat-intelligence-enrichment −6%, instrument-identity-resolution −6%) — slices the capable baseline
already handles, or whose scope overlaps a sibling skill.

## The finding that matters most

The decomposed picture is **smaller and noisier than the monolithic baseline it replaces** — and that
is the real result. The single-skill flow reported +25% to +58% per blueprint; decomposed, the range
is −13% to +75% around a +16.4% mean. A narrow skill measured against a *narrow, auto-generated* eval
suite has far less headroom, because the capable baseline already answers most of one skill's
scenarios. Ceiling compression, not weak skills.

## Limits to keep in view

- **Grader fragility is the dominant data loss**, not skill quality: 6 of 33 skills returned
  malformed grading JSON three times in a row and were never scored. Hardening the grader to repair
  truncated JSON would recover them.
- **Auto-generated evals are not fixed** — the same model drafts the expectations each run, so a
  skill's measured lift partly tracks how discriminative its suite happened to be. Use a **fixed,
  hand-checked** suite before trusting a number.
- **Decomposition is non-deterministic.** A separate run produced 15 TradingAgents skills, not 16;
  an earlier run collapsed Aurora to 1–2. Per-skill numbers are only comparable across runs once the
  decomposition is pinned (temperature 0 / caching).
- **A skill is a description, not an executable spec** — it improves understanding and decisions, not
  execution. For executable scripts inside a skill, see [[skill-scripts-graaljs]].
- **New direction:** this is outside the docs/04 C1–C18 claim set, not one of the original hypotheses.
- **Productized 2026-11-14:** the builder emits a skill itself now — `smith build --target skill` —
  so this POC's mechanism is a shipped output target, not just a probe ([[skill-output-target]]).

See [[what-the-pocs-prove]].
