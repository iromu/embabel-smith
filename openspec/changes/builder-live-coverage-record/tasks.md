# Tasks

## 1. State the coverage consequence

- [x] 1.1 In `docs/05-architecture.md` §"The live group is opt-in (`test.excludedGroups`)", add a
      short statement of what the default run therefore does not evidence — the builder's publish path
      against a real model, the runtime's ingest-and-run, the studio's REST surface and the
      improvement loop — and verify each named path is one of the four tagged tests' subject, not an
      invention.
- [x] 1.2 Name the cheap next live test the same page already has in `docs/08-builder.md` (a small
      blueprint with an expected spec and skill) as the affordable response, and verify the two pages
      describe the same candidate.
- [x] 1.3 Mirror one sentence of the consequence in the README run book's live-test section, and
      verify it states the same paths in the run book's own voice rather than duplicating the page.

## 2. Name only tests that exist

- [x] 2.1 Delete "plus the builder's example suites" from the list in `docs/05-architecture.md`, and
      verify `grep -rn '@Tag("live")' prod/*/src/test` returns exactly the tests the sentence now
      names.
- [x] 2.2 Record that grep as the check beside the list, and verify running it reproduces the page's
      list on the current tree.
- [x] 2.3 Verify `docs/08-builder.md` and `docs/05-architecture.md` no longer disagree about whether
      the examples are part of `make test-live`, quoting both sentences.

## 3. Close the backlog item

- [x] 3.1 Replace the *Test coverage* item in `docs/12-backlog.md` with a pointer to the section that
      now carries the statement, and verify the page does not still describe the four tests as merely
      `@EnabledIfEnvironmentVariable`-gated (they are tagged as well since 2026-11-17).
- [x] 3.2 Run the wiki lint the repo rule prescribes for a `docs/` edit and verify no cross-link to
      `05-architecture.md` broke; no heading is renamed by this change. **Closed 2026-11-20.** Both
      reasons this was held open have gone: the `docs/` edits are committed, and the `git-wiki` skill
      resolves at `.agents/skills/git-wiki`, so the lint ran as its rules define it — over the committed
      tree, on the link syntax the wiki actually uses (`[[page-slug]]`, per the skill's "Wiki links" rule).
      Result: **44 pages (excluding `index.md`), 451 `[[links]]`, 0 broken targets, 0 orphans**, and
      `.wiki/index.md:last_commit` still `7805939` with its "1083 tracked files changed since it" note,
      so the scoped edits did not falsely advance the checkpoint. On the `docs/` side, markdown links
      resolve with 0 broken and `docs/05-architecture.md` has 8 inbound links.
      Caveat worth keeping: the first two attempts at this check counted markdown `[text](target.md)`
      links across `docs/` and `.wiki/` together and reported 81 links and **44 orphans** — every wiki
      page. That was the instrument, not the wiki. A wiki lint must parse `[[slug]]`; a markdown-link
      scan of this root reports the entire wiki as orphaned.
      The Pages re-publish remains owed by a human: `wiki-pages.yml` deploys the default branch and this
      work is on `poc/iteration-2`.
- [x] 3.3 Verify the change touched no code: `git status --short` lists only `docs/` and `README.md`.
      **Verified by substitution**, because the shared working tree also holds
      `builder-publish-location`'s `prod/` edits, so the unscoped check cannot read clean here: the
      scoped `git diff --stat -- docs/05-architecture.md docs/12-backlog.md README.md` is the whole of
      this change, and no `.java`, `pom.xml` or `Makefile` line belongs to it.

## Verification record (checked 2026-11-18, every sentence against the tree)

- 1.1 — each named path is the subject of exactly one tagged test, not an invention: builder
      `LiveBuildIntegrationTest` javadoc — "generate both candidates → validate → write → score both →
      recommend → security → lifecycle → publish", single-shot asserted to a published pack; runtime
      `LiveRunIntegrationTest` — "live runs end-to-end (source → ingest → run → trace)"; studio
      `improve/RestIntegrationTest` — "drive the three production surfaces … with the real,
      model-backed cores wired"; studio `improve/LiveLoopIntegrationTest` — "one full loop iteration …
      with the real refine and re-eval, publishes an improved pack".
- The substitutions the new text claims offline are real: builder `DualCandidateBuildTest.java:63` and
      `skill/SkillBuilderServiceTest.java:63` pass lambda generators and a scripted `SpecPromptCaller`;
      runtime `AgentSpecRunServiceTest.java:23` states in its own javadoc that "the success path is
      covered by the endpoint-gated integration test" (quoted from that file); studio
      `rest/BuildControllerTest.java:32` and `rest/RunControllerTest.java:30` construct the controllers
      over lambda cores and `rest/ImproveControllerTest.java:45` mocks `LoopService`.
- 1.2 — the two pages name the same candidate, in the same words: `docs/05-architecture.md` ("a small
      blueprint with a known expected spec and skill, which can be asserted rather than merely
      recorded") and `docs/08-builder.md:254-255`.
- 2.1/2.2 — `grep -rn '@Tag("live")' prod/*/src/test` returns exactly the four files the sentence
      names, and `prod/embabel-smith-builder/src/test/java/com/embabel/smith/builder/` has no
      `example/` package, so the removed suites are genuinely absent.
- 2.3 — `docs/08-builder.md:253` ("the examples are not part of it") and
      `docs/05-architecture.md:181-183` ("a set of hand-started scripts under `examples/` that
      `make test-live` does not run") now agree; `examples/` holds `smith-cli.sh`, `generate-spec.sh`,
      `generate-skill.sh` and `generate-blueprint.sh`.
- 3.1 — `docs/12-backlog.md` §Test coverage is closed and points at the section; its two `Check:`
      commands both pass: the tag grep returns four files and `grep -n 'does not prove'
      docs/05-architecture.md` hits line 185.
- 3.2 — the `git-wiki` lint skill is not installed at a resolvable path on this machine, so the lint
      was not run as a command; the assertion it exists to protect was checked directly instead. No
      page under `.wiki/` links `docs/05-architecture.md` (the wiki uses `[[wikilinks]]`), none
      mentions the live group, `test-live` or the example suites, and the only two pages naming
      `LiveBuildIntegrationTest` (`features/smith-builder.md:150`,
      `risks/model-calls-go-through-ai.md:66`) are untouched by this wording. No heading is renamed, so
      no re-ingest or re-publish is forced; running the lint anyway is left to whoever next has the
      skill available.
- 3.3 — this change touched only `docs/05-architecture.md`, `docs/12-backlog.md` and `README.md`. The
      `git status --short` check cannot be run clean on this tree today: an unrelated change
      (`builder-publish-location`, plus `docs/06-spec-sources.md`) has `prod/` files modified in the
      same working tree, so the listing shows more than this change wrote. Verified by scoping the
      diff instead: `git diff --stat -- docs/05-architecture.md docs/12-backlog.md README.md` is the
      whole of this change, and no file under `prod/`, `poc/`, `pom.xml` or `Makefile` carries an edit
      belonging to it.
