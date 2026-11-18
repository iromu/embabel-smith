# Proposal

## Direction change (2026-11-17)

The three JUnit suites this proposal specified were built and run once. That run took 49.9 minutes
of local endpoint time across nine cells, queued on the order of two hundred model calls, and
finished with **zero** cells published — every one blocked at a gate or errored on an endpoint 500
or Embabel's 60 s per-interaction timeout. The extraction suite was also found to be running a
prompt written for a *codebase* (`business_process_discovery.jinja`: "Analyse the entire codebase",
"Do not describe classes, functions, modules or files") over documents that are already
business-process descriptions, losing detail for a full-context call.

So the example verification is no longer a test suite. What replaced it:

- **Kept**: the opt-in-by-tag machinery (sections 1 of `tasks.md`) — the root pom's
  `test.excludedGroups`, `@Tag("live")` on the four endpoint-gated tests, and `make test-live`. It
  serves those four tests regardless of what the examples do.
- **Removed**: the three suites, the `builder/example/` harness, `examples/cases/`, and the
  `examples/generated/` tree.
- **Added instead**: hand-launched scripts, `examples/generate-spec.sh` and
  `examples/generate-skill.sh`, that drive the product CLI for one blueprint each and state their
  own model cost in their headers. See `examples/README.md`.
- **Deferred**: a live test worth having — a small blueprint with a known expected spec and skill,
  which can be asserted rather than recorded.

## Why

The builder has no real-model verification that scales. One hand-written live test exists
(`LiveBuildIntegrationTest`), and the only gate on it is `@EnabledIfEnvironmentVariable` — while the
root `Makefile`'s `test` target deliberately sources `~/.config/env/llm.env` and exports
`OPENAI_CUSTOM_BASE_URL` *precisely so that those tests run*. So `make test` is green only when a
local endpoint happens to agree, and there is no way to ask "does generation actually work across
examples, techniques and output targets, and how good is the result?" That question is the one the
POCs answered by hand once, in throwaway code.

This change makes real-model verification a repeatable, opt-in part of the project: excluded from the
reactor by default, runnable on demand, and leaving behind a folder structure a human can inspect.

## What Changes

- Live model tests become **opt-in by tag**. `@Tag("live")` on every endpoint-driven test — the four
  that exist today (builder `LiveBuildIntegrationTest`, runtime `LiveRunIntegrationTest`, studio
  `improve/RestIntegrationTest`, studio `improve/LiveLoopIntegrationTest`) and the new ones — with
  the root pom's Surefire configuration excluding that group by default through an overridable
  property. `mvn test` therefore stays green with no endpoint configured.
- A `make test-live` entry point that sources the endpoint environment and runs the live group.
  **This is a behaviour change for the whole reactor, not just the builder**: without it, the tag
  would silently stop the runtime's and the studio's live tests from ever running, since they run
  today under
  `make test`.
- `@EnabledIfEnvironmentVariable` is **kept** alongside the tag. The tag is the opt-in; the env check
  makes an opted-in run on a machine with no endpoint skip rather than error.
- A **blueprint extraction suite** that runs docs ingestion over `examples/blueprints/` and writes the
  extracted description to `examples/cases/<slug>/description.md`.
- A **generation suite** that consumes those extracted descriptions and builds each example across
  generation techniques and output targets, publishing into
  `examples/generated/<slug>/<target>-<technique>/` — with the build's scratch directory pointed into
  that folder so the *rejected* candidate is inspectable too, and overwrite enabled so reruns do not
  collide.
- A **reporting suite** that aggregates the per-cell evidence into `examples/generated/REPORT.md`,
  asserting that every expected cell produced a result and recording eval scores and skill lift
  without gating on them.
- Assertions are **contract-level**: a result at a known stage, artifacts written, the pack validates,
  report fields present, the matrix complete. Quality is reported, not asserted.

## Capabilities

### New Capabilities

- `live-model-verification`: running the builder against a configured real model on declared examples,
  on request rather than by default; the artifacts each run leaves for inspection (extracted
  description, published artifact, rejected candidate, per-cell result, aggregate report); and which
  of those outcomes are asserted versus reported.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for the gating
convention is `docs/05-architecture.md` (which already carries the build entry points) alongside the
root `Makefile` and `README.md`.

## Impact

- **Root `pom.xml`**: Surefire gains a configuration block with an overridable excluded-groups
  property. Applies to every module, which is the point.
- **`Makefile`**: new `test-live` target; `test` and `test-module` keep their env export but no longer
  determine whether live tests run.
- **`prod/embabel-smith-builder`**: new test sources (the three suites plus a shared example harness),
  `@Tag("live")` on the existing live test, and a repo-root lookup helper — Surefire's working
  directory is the module directory, so reaching `examples/` needs one.
- **`prod/embabel-smith-studio`**: `@Tag("live")` on its two endpoint-gated tests.
- **`prod/embabel-smith-runtime`**: `@Tag("live")` on `LiveRunIntegrationTest`. It is gated by the
  same `OPENAI_CUSTOM_BASE_URL` condition and so runs under `make test` today, exactly like the
  studio's two; the plan's "three existing tests" undercounted it.
- **`examples/`**: gains `cases/` and `generated/` trees. Whether `generated/` is committed or
  gitignored is deliberately deferred — it is one `.gitignore` line, decidable without touching a
  test.
- **Depends on**: `builder-docs-ingestion`. The extraction suite drives ingestion through the builder;
  while ingestion lives in the studio, the builder's own test sources cannot reach it.
- **Cost**: a full agent-spec build is two generations plus eval tasks across up to three refinement
  rounds for each of two candidates; a skill build is one generation, one eval-suite draft, two arms
  per scenario and a grade per scenario. The matrix size is therefore a declared, bounded choice, not
  an accident.
