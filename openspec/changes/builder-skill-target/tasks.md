# Tasks

## 0. Resolve open design decisions

- [x] 0.1 Resolve the design open questions (eval arms, evals source, default lift margin,
      lifecycle reuse) and record the decisions in `design.md`. Verify `design.md` no longer lists
      them as open.

## 1. Skill generation

- [ ] 1.1 Port `SkillGenerator` into the builder (description → `SKILL.md`; name fixed by the
      description, description + body model-supplied via the builder's `Ai`). Verify with an offline
      test that injects a canned model answer and asserts a well-formed `SKILL.md`.
- [ ] 1.2 Port `SkillMdValidator` (frontmatter constraints). Verify an offline test rejects each
      constraint violation (bad name, over-long description) and accepts a valid skill.

## 2. With/without-skill evaluation

- [ ] 2.1 Port `EvalSuite`/`EvalSource` and `EvalRunner` (each scenario run with the skill and
      without it). Verify an offline test (fake model) runs every scenario in both arms.
- [ ] 2.2 Port `SkillGrader` (LLM-as-judge against verifiable expectations) and `Benchmark`
      (with vs without pass rate → lift). Verify an offline test computes the lift from canned
      gradings.

## 3. Gate + publish

- [ ] 3.1 Add the lift gate (block publication when the lift is not `> 0`; report per-scenario
      grading on block). Verify an offline test that a zero/negative-lift skill is not published and
      a positive-lift skill is.
- [ ] 3.2 Generalize the publisher to a named directory and add a skill writer (`SKILL.md` +
      `evals.json`). Verify an offline test that a skill publishes to `folder:` and that a name
      collision blocks without `--overwrite`.

## 4. Wiring + docs

- [ ] 4.1 Add `target` to `BuildOptions` (default `agent-spec`) and a `--target` CLI flag, and
      dispatch the skill target to `SkillBuilderService`. Verify `smith build --help` lists the flag
      and a `--target skill` build routes to the skill path.
- [ ] 4.2 Reuse the security scan and lifecycle stamp for the skill path and update the builder
      `README.md`. Verify the skill build report includes the lift, the scan, and the lifecycle
      state; run the builder's offline suite (green, no endpoint) and a live `--target skill` build.
