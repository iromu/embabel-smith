# Design

> **Partly superseded 2026-11-17.** Read the Direction change at the top of `proposal.md` first.
> The tag-gating decisions below (the excluded-groups property, keeping the env check alongside the
> tag, the one supported entry point) still hold and are shipped. The decisions about the example
> pipeline — extraction as a stage, the cell matrix, the aggregate report, and the alphabetical
> suite order that ordered them — were built, run once, and removed. `examples/README.md` is now
> the design of that part.

## Context

See `proposal.md` for motivation; `specs/live-model-verification/spec.md` for the contract.

Facts this design has to work with:

- Surefire is declared in the root pom's `pluginManagement` with **no** `<configuration>` block, and
  no `@Tag`, `<groups>` or `<excludedGroups>` appears anywhere in the repository. Live tests are
  gated solely by `@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL")`.
- The root `Makefile` sources `~/.config/env/llm.env` and exports `OPENAI_CUSTOM_*` inside `test` and
  `test-module` — so live tests run today, by design. Excluding them is a reactor-wide behaviour
  change, and three tests are affected beyond the builder.
- Surefire's working directory is the module directory. The precedent in
  `skillevaluator/Tier1RunnerIntegrationTest` works only because its fixtures are module-local; it does
  not reach the repository root.
- `--technique` applies to the `agent-spec` target only, so the matrix is not a full cross product.
- `BuilderService` leaves both candidates in the `BuilderAgent` scratch directory, which
  `BuildCommand` deletes in a `finally`.
- The publisher's collision gate rejects a same-named artifact unless `overwrite` is set.

## Goals / Non-Goals

**Goals:**

- `mvn test` is green with no endpoint configured, and one command runs everything against the model.
- A run leaves a folder structure that answers "what did it produce, and how good was it" without
  reading test code or console scrollback.
- The suites are cheap to extend with an example and honest when a stage did not run.

**Non-Goals:**

- Not a quality gate. Nothing here fails a build because a score is low; that is what the report is
  for.
- Not CI. There is no endpoint in CI and no plan to add one; the live group is a developer- and
  machine-opt-in.
- No change to what a build does, or to any production class. This change touches test sources, the
  root pom, the `Makefile`, and `examples/`.

## Decisions

**D1 — `@Tag("live")` plus a root-pom `excludedGroups` property, not a `junit-platform.properties` file.**
Surefire's configuration becomes `<excludedGroups>${test.excludedGroups}</excludedGroups>` with the
property defaulting to `live`. This lives in `pluginManagement` so every module inherits it and a new
module cannot forget it. Rejected: a `junit-platform.properties` carrying
`junit.jupiter.tags.exclude.tags`, which excludes the group for *every* JUnit Platform launcher
including the IDE — and the IDE is precisely where a developer wants to run one live test by hand.
Rejected: per-module Surefire blocks, which duplicate the policy three times.

**Trap, documented because it is silent:** `mvn test -Dgroups=live` selects **nothing**, because the
default `excludedGroups` still excludes `live` and Surefire applies both. Opting in must clear the
exclusion as well — which is what the Makefile target exists to encode.

**D2 — `make test-live` is the only supported opt-in, and it runs the three suites as one invocation
with `<runOrder>alphabetical</runOrder>` set on the builder module.**
The target sources the endpoint environment exactly as `test` does and passes
`-Dtest.excludedGroups= -Dgroups=live`. The three suites are named so that alphabetical order *is*
pipeline order — `BlueprintExtractionLiveTest`, `ExampleGenerationLiveTest`,
`ExampleReportLiveTest` — and `runOrder` is set only in the builder module's own Surefire
configuration, not globally, because the ordering requirement belongs to these classes. Rejected:
three sequential Maven invocations, which is three Spring context boots to pay for the ordering that
one local `runOrder` gives deterministically; and relying on Surefire's default `filesystem` order,
which is not an order at all. The safety net is D5: an out-of-order generation run fails loudly rather
than quietly extracting.

**D3 — `@EnabledIfEnvironmentVariable` stays.**
The tag answers "should this run by default". The env condition answers "can this run here". Removing
the second turns an opted-in run on a laptop with no endpoint into a wall of failures instead of a
wall of skips.

**D4 — An example is a declared folder: `examples/cases/<slug>/case.yml`.**
It names its blueprint (a directory under `examples/blueprints/`), the targets and techniques to run,
and the eval tasks to score against. `@ParameterizedTest` over a directory scan means a new example is
a new folder. Rejected: a static list in a test method, which makes adding evidence a code change; and
placing `case.yml` inside `examples/blueprints/<Name>/`, which mixes third-party input documents with
this repository's derived configuration and makes the eventual "commit or ignore" decision harder,
since everything under `cases/` is derived and everything under `blueprints/` is not.

**D5 — Stages are separate suites, and generation refuses to substitute for extraction.**
Extraction writes `examples/cases/<slug>/description.md`. Generation reads it and, when absent, fails
that example naming `BlueprintExtractionLiveTest` as the producer. Rejected: re-extracting per run,
which pays a 200K-character model call per cell and mixes extraction drift into a generation diff; and
extraction-with-cache-and-refresh, which leaves two sources of truth for the same file. The cost is a
required order, which D2 makes deterministic.

**D6 — Cell layout `examples/generated/<slug>/<target>-<technique>/`, with the scratch directory
pointed inside it.**
Each cell holds `report.md` (the `BuildResult.report()` verbatim), `result.json` (stage, score, lift,
issues, timings, model), `published/` (the publish target) and `candidates/` (the `BuilderAgent`
scratch). Pointing scratch into the cell is the whole reason the harness does not just call the CLI:
it makes the *rejected* candidate inspectable, which is the evidence you need when judging whether a
technique is actually producing a better process. Cells are built with `overwrite = true`, or the
collision gate blocks every rerun at `publish`.

**D7 — The matrix is declared, bounded, and not a full cross product.**
`--technique` applies to the `agent-spec` target only, so each example contributes two agent-spec
cells (single-shot, embabel) and one skill cell — nine cells across the three committed blueprints.
The report suite asserts completeness against that same declaration, so a cell that silently did not
run is a failure, and a suite that generated nothing cannot pass.

**D8 — Repo root comes from a Surefire system property, with a walk-up fallback.**
The builder's Surefire configuration passes `smith.repoRoot=${maven.multiModuleProjectDirectory}`;
the helper falls back to walking up from `user.dir` until it finds a directory holding both `pom.xml`
and `examples/`, so an IDE run still resolves. Rejected: a hardcoded `../../../../` from the module
directory, which breaks the moment the module moves, and silently resolves to some other project.

**D9 — Quality is recorded, not asserted; the completeness check is the assertion that matters.**
Contract assertions are: a result at a known stage, artifacts present, the pack validates when one
published, and every declared cell produced a result. The motivation is concrete — three endpoint-gated
prod tests already fail on model answers rather than code, and a suite that goes red for that reason
stops being run.

## Risks / Trade-offs

- **Excluding the group silently darkens the runtime's and the studio's live tests.** → `test-live`
  lands in the same change, and the four existing tests are tagged as part of it rather than in a
  later cleanup.
- **`result.json` becomes an accidental contract.** Nothing consumes it programmatically, so a field
  rename is free — until a diff tool or a wiki page starts reading it. → The schema is documented in
  the harness class that writes it, and the aggregate report renders from it rather than re-deriving.
- **A committed `examples/generated/` tree would churn on every model change** and make ordinary diffs
  unreadable. → Deferred deliberately as one `.gitignore` line; the suites do not care either way, so
  the decision can be made with evidence from a few runs.
- **Cost is invisible until it is running.** Nine cells is roughly two hundred model calls before
  extraction. → The matrix is declared in `case.yml`, so the size is reviewable before it runs, and
  `-Dtest=ExampleGenerationLiveTest -Dsmith.example=aurora` narrows a run to one example.
- **Alphabetical ordering is a naming convention carrying load.** Renaming a suite could break the
  order. → The generation suite's refusal to extract (D5) turns that breakage into a loud failure
  rather than a wrong result.
- **The `embabel` cell is expected to block** at `build` with `DANGLING_TYPE_REF` (POC 24 measured
  0/6 valid). → It stays in the matrix as an evidence cell so the report shows the limitation rather
  than hiding it; the existing seam assertion in `LiveBuildIntegrationTest` remains the test that
  pins what that technique does guarantee.

## Open Questions

- Whether the `embabel` cell should *assert* that it blocks at `build` with `DANGLING_TYPE_REF`
  (catching the day the upstream compiler starts emitting types) or stay evidence-only so that day is
  a green surprise. Either answer changes one assertion, not the structure.
- Whether the extracted `description.md` files are committed alongside the report, or only `generated/`
  is treated as derived. Both are derived; they differ in how reviewable a run is after the fact.
