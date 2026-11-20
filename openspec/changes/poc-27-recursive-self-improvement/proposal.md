# Proposal

## Why

The improvement loop in `prod/embabel-smith-studio` improves one thing: the prompts of the steps
that failed. It cannot touch the generator that wrote them, and the builder generates no `scripts/`
at all — `poc-26` built the in-JVM runner for JavaScript skill scripts and nothing upstream feeds
it. Recursive self-improvement is the untested claim sitting on top of both: can a builder improve
the process that builds, including the part of itself that does the improving, without grading its
own homework.

The project already has the failure mode on record. A builder-built pack is stamped with a baseline
equal to its own build-time score, pinned at the score gate's maximum, so the loop's strict
baseline comparison can never pass and the loop jams until a fallback proves the fix instead (see
[`docs/10-self-improvement.md`](../../../docs/10-self-improvement.md) and `GateChain`'s failure-fix
fallback). That is self-grading, generalised: any improver that scores itself optimises its own
scorer, not its work. A POC is the only place to find out how much of the loop can be made
self-referential before that becomes fatal.

## What Changes

- A new POC module `poc/poc-27-recursive-self-improvement`, added to the `poc` reactor behind the
  `poc` Maven profile. `prod/` is not modified.
- **A frozen benchmark authored as evidence.** A hand-written task set with human-authored expected
  outputs, plus a frozen trace corpus with human "is this a genuine defect" labels, both checked in
  under `src/main/resources/` and scored by a deterministic scorer. Its baseline pass-rate and its
  measured repeat-to-repeat variance are recorded *before* any tuning, because a strict comparison
  against an LLM-judged score without a noise floor is a coin flip — and the repo already measured
  that variance (`docs/08-builder.md`: single-shot multi scored 3/3 on one run of the same matrix,
  1/3 on a re-run).
- **A self-description as build input.** A hand-written description of embabel-smith's own process,
  distilled from `docs/05`, `docs/08` and `docs/10`. Not produced by `smith blueprint`: that prompt
  asks for a *codebase* and its output already *is* a blueprint, as `docs/08-builder.md` states.
- **The mutable surface widened in three steps.** Generated agent specs (plural) describing the
  build process; generated JavaScript skill scripts, run through `poc-26`'s GraalJS engine; and
  finally a generated improvement policy — detection and refinement decisions — expressed as a
  skill script and an improver pack that the runtime executes.
- **A two-loop harness.** An offline inner loop scores generated policies against the labelled
  trace corpus at zero model calls; a paid outer loop runs the surviving policy as an improver and
  measures the packs it produces against the frozen benchmark, across at least two generations.
- **A generation archive.** Every variant, its parent, its score, retained rather than hill-climbed,
  so the POC can also report whether archive selection beats greedy selection.
- **The recursion is measured as a derivative.** Generation N+1's improver must produce better
  generation-N candidates than generation N's improver produced. A single score improvement does not
  distinguish self-improvement from one hill-climb and will not be reported as such.
- The builder's own HTTP surface is **out of scope**. The referee runs as a separate hand-started
  process, consistent with the studio's existing rule that sidecars are reported and never started
  (`docs/09-studio.md`).

## Capabilities

### New Capabilities

- `recursive-self-improvement`: what a self-improving loop may and may not mutate, what scores a
  candidate, what a generation must demonstrate to count as an improvement, and how generations are
  retained. This is the durable behaviour the POC exists to measure; `poc/` is frozen evidence and
  product work never touches it.

### Modified Capabilities

None. `openspec/specs/` is empty — the planning trees were consolidated into `docs/` and deleted —
and no product behaviour changes. `docs/04-poc-run-results.md` and the POC's own README carry the
result, per the conventions in `docs/04` and `AGENTS.md`.

## Impact

- **New module**: `poc/poc-27-recursive-self-improvement`, registered in `poc/pom.xml` (`<modules>`
  and `dependencyManagement`), depending on `poc-26-graaljs-skill-scripts` for the script engine.
- **Reuses, unchanged**: `builder/core/ProcessSpecGenerator` and `ProcessDraft`,
  `common/validation/ProcessValidator`, `builder/eval/{EvalTask,EvalScorer,InvokerFactory}`,
  `studio/improve/GateChain` (its two-argument constructor already accepts an injected task list and
  invoker factory), `runtime/trace/TraceStore`, `builder/skill/*`.
- **Deliberately not reachable from the mutable set**: `EvalScorer`, called statically inside
  `GateChain.reeval` and listed in `docs/10-self-improvement.md` as a non-injectable seam — for this
  POC that limitation is the referee's tenure, not a defect. `AgentPlatform` and the runtime's gates.
  The frozen task set and labelled corpus.
- **Cost**: a skill build already runs ~15–19 model calls and the eval spends four per scenario
  (`docs/08-builder.md`), so the live outer loop is budgeted in generations and the inner loop is
  designed to cost nothing.
- **Conventions that bind it**: every source file carries the author and SPDX header; the POC README
  ends with a Conclusion and shared-results section and carries a mermaid diagram; `poc/` is frozen
  after the fact; commit, never push (`docs/05-architecture.md`).
- **Literature**: the README cites the self-improvement line — Gödel machines, STOP, the Darwin Gödel
  Machine, Gödel Agent, ADAS, Voyager, LLMs-as-Tool-Makers, Agent Workflow Memory, Reflexion,
  Self-Refine, STaR, and the reward-overoptimization result — with arXiv identifiers **verified
  against arXiv before the README is frozen**, since `poc/` is not edited afterwards.
