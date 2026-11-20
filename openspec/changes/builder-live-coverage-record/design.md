# Design

## Context

- The gating machinery exists and works: root `pom.xml:51` `<test.excludedGroups>live</test.excludedGroups>`
  applied through `pom.xml:287` `<excludedGroups>${test.excludedGroups}</excludedGroups>` in the
  Surefire `pluginManagement` block, and `Makefile:35` as the documented entry point.
- Exactly four tests carry the tag: builder `LiveBuildIntegrationTest.java:45`, runtime
  `LiveRunIntegrationTest.java:33`, studio `improve/LiveLoopIntegrationTest.java:46`, studio
  `improve/RestIntegrationTest.java:46`. Grepping the builder test tree for the tag returns the one
  builder file — no example suites.
- `docs/05-architecture.md` §"The live group is opt-in (`test.excludedGroups`)" explains the
  mechanism thoroughly, including that `mvn test -Dgroups=live` alone selects nothing, and closes its
  list at line 179 with "plus the builder's example suites (see [`08-builder.md`](08-builder.md))" —
  which is no longer true.
- `docs/08-builder.md:253` states the opposite of that clause: the examples are **not** part of
  `make test-live`. The two pages disagree; the tree sides with `08`.
- What the four tests are the only execution of: the builder's publish path end to end against a real
  model, the runtime's ingest-and-run, the studio's REST surface and the improvement loop. The
  builder page's claim about the `embabel` technique rests on the live build test, which the default
  run never runs.
- No page in `docs/` or `README.md` states any of this as a coverage consequence today: searching for
  "untested", "never executed", "not covered" and "no coverage" hits only `docs/12-backlog.md:124`,
  the backlog line this change closes.
- `builder-live-example-tests` already shipped the machinery and its *Direction change (2026-11-17)*
  removed the example suites deliberately, with the cost (about two hundred model calls in 49.9
  minutes, zero cells published) as the reason.
- There is no test CI runner to fix: `.github/workflows/` contains only `wiki-pages.yml`. So the
  guarantees the four tests carry are unexercised by any automated run, default or otherwise, until a
  person runs `make test-live`.

## Goals / Non-Goals

Goals:

- A reader of the architecture page learns what a green default run does **not** evidence.
- The list of live tests matches the tag in the tree, with the check that keeps it matched.
- The backlog item closes by pointing at the page, not by restating it.

Non-Goals:

- No new live test, no new tag, no change to what runs by default.
- No revival of the example matrix — refused by the earlier change's own record.
- No CI pipeline work. The only workflow in the repository is `.github/workflows/wiki-pages.yml`;
  nothing here runs the reactor on a push, so "never executed in CI" is true of every test — the item
  this change closes is about the record, and standing up a test runner is its own decision.
- No change to the cheap live test `docs/08-builder.md` already flags as the next one worth adding
  (a small blueprint with an expected spec and skill); it stays a named future item, unchanged.

## Decisions

**D1 — Fix the existing section rather than add a coverage page.** The section already owns the live
group and is where a developer lands from `make test-live`; a second page would be the third place
the same rule is written. Rejected: a `docs/`-level "test coverage" page — there is one fact to
record, not a corpus.

**D2 — State the consequence as the paths that go unexercised, not as a percentage or a list of
tests.** The tests are already listed; what is missing is what they alone prove. Rejected: a
"coverage: partial" label — it does not tell a reader which guarantee to distrust.

**D3 — Record the tree check beside the list.** The page already models this habit elsewhere (the
backlog records a `Check:` per item), and the rot here was a list outliving the tests it named.
Rejected: generating the list from the tree at build time — a docs page for four tests does not
need a generator.

**D4 — Docs-only, and say so in the tasks.** No code, no pom, no Makefile edit; the item's residual
was a missing sentence, and the earlier change already built everything else.

## Risks / Trade-offs

- **A recorded gap can go stale in the other direction** — a later change adds offline coverage and
  nobody deletes the sentence. Mitigated by naming the check (the tag grep) next to the claim, so the
  next reader can falsify it in one command.
- **Naming the untested paths invites "then fix it now".** The answer is already written in
  `docs/08-builder.md`: the affordable next step is the small assertable live example, and the
  expensive one was measured and refused. The page should point there rather than leave the gap
  looking unconsidered.
- **The wiki cross-links 44 pages**, so any edit to `docs/05-architecture.md` wants the wiki lint the
  repo rule prescribes; this change adds no new page and changes no vocabulary, so the risk is a
  broken link only if a heading is renamed — which the tasks forbid.
