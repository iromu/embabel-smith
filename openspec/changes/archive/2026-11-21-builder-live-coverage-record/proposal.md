# Proposal

## Why

The *Test coverage* item in `docs/12-backlog.md` was written before the live group existed, and its
complaint has since been answered only halfway.

Answered: the four endpoint-gated tests are now tagged and excluded by default, with one documented
opt-in. `pom.xml:51` `<test.excludedGroups>live</test.excludedGroups>`, applied at `pom.xml:287`
`<excludedGroups>${test.excludedGroups}</excludedGroups>`, with `Makefile:35`
`test-live: ## Run ONLY the live, endpoint-driven tests (needs ~/.config/env/llm.env)`. Each of the
four carries `@Tag("live")` (`LiveBuildIntegrationTest.java:45`, `LiveRunIntegrationTest.java:33`,
`improve/LiveLoopIntegrationTest.java:46`, `improve/RestIntegrationTest.java:46`) and keeps its
`@EnabledIfEnvironmentVariable`. `docs/05-architecture.md` §"The live group is opt-in" and the README
run book both explain the mechanism, including the flag pair that a half-typed invocation silently
selects nothing.

Not answered — two sentences' worth of text, which is why this is a docs change and not code:

- **Nothing states what the default run therefore fails to prove.** The backlog's own words are still
  true: "the offline suite passes and those guarantees are simply untested". The four tests are the
  only execution of the publish path, the runtime's run path and the loop end to end, and
  `docs/08-builder.md` leans on the live build test for the one claim it makes about the `embabel`
  technique's upstream limit. `docs/05-architecture.md` names the four tests but never says what
  rests on them, and a search of `docs/` and `README.md` for any statement of untested coverage
  returns only the backlog line itself. A green `make test` is read as full coverage because nothing
  says otherwise.
- **The page names tests that are not in the tree.** `docs/05-architecture.md:179` ends its list with
  "plus the builder's example suites (see [`08-builder.md`](08-builder.md))". Those suites were
  removed on 2026-11-17 — `openspec/changes/builder-live-example-tests/proposal.md` records "Removed:
  the three suites, the `builder/example/` harness…", and `docs/08-builder.md:253-255` says
  "`make test-live` still runs the four endpoint-gated integration tests that carry `@Tag("live")`; the
  examples are not part of it". Grepping the builder's test sources for the tag returns exactly one
  file, `LiveBuildIntegrationTest.java`. The code wins; the page is wrong.

## What Changes

- `docs/05-architecture.md` §"The live group is opt-in" gains a short *what this leaves unverified*
  statement: the paths that run only in the live group, and that a green default run does not
  evidence them.
- The same list stops naming the removed example suites and instead states how to check the list
  against the tree (the tag grep), so it cannot silently rot again.
- The README run book's live-test section gains the same one-sentence consequence, in its own words.
- `docs/12-backlog.md` *Test coverage* is struck as resolved-by-record, pointing at the page that now
  carries it.

No test, pom or Makefile change. The gating machinery is not being re-proposed: `builder-live-example-tests`
shipped it, and that change's *Direction change* rules out reviving the example matrix.

## Capabilities

### New Capabilities

- `live-coverage-record`: the project's statement of what its default test run does not prove — the
  guarantees that rest only on tests excluded from it, kept as the list of tests that actually carry
  the exclusion tag, and surfaced where a reader would otherwise read a green default run as full
  coverage.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — consolidated into `docs/` on
2026-11-15. The durable homes are `docs/05-architecture.md` (which owns the build entry points and the
live group) and `README.md`'s run book.

## Impact

- **`docs/05-architecture.md`**: the live-group section, two sentences.
- **`README.md`**: the live-tests section, one sentence.
- **`docs/12-backlog.md`**: the *Test coverage* item closes with a pointer.
- **Code**: none. The tag, the property and the entry point already exist and already work.
- **Wiki**: the page is cross-linked from `.wiki/`, so the vocabulary-unchanged edit still wants the
  wiki lint the repo rule prescribes; no page names change, so no re-ingest is forced by this change.
