# Tasks

## 1. Report the source's own location

- [x] 1.1 Change `PublishResult.location` from a `Path` to the location as the source states it, and
      update its javadoc so the promise is "where the artifact lives", not "the directory it was
      written into". Verify by compiling the builder module.
- [x] 1.2 Have `FolderPublisher` report the written directory in that form and `GitPublisher` report
      the repository it committed to with the artifact's path inside it, never a path under its
      working tree. Verify with the existing `PublisherTest` folder and git cases, adjusted to the new
      form, still green.
- [x] 1.3 Update the four surfacing sites — `BuildResult.report()`, `skill/SkillBuildResult.report()`,
      studio `rest/dto/PublishInfo`, studio `loop/LoopService` — and verify a `git:` build's report
      `published:` line names the git source, and that studio test-compile is clean (a builder API
      change has gone unnoticed in the studio before).

## 2. Prove a skill over git:

- [x] 2.1 Add an offline test that publishes a `SkillPublishable` through `GitPublisher` into a bare
      repository created in a `@TempDir`, re-clones it, and asserts the commit carries `SKILL.md`,
      `evals.json` and `lifecycle.yml`. Verify by running the builder module's tests.
- [x] 2.2 In that test, assert the reported location names the git source and does not resolve under
      the system temporary directory, and that the assertion holds after `close()` has run — the
      condition the pack test never checked.
- [x] 2.3 Verify the collision and `--overwrite` rules apply to a skill over `git:` as they do to a
      pack, in the same test or a sibling, so the `Publishable` generalization is exercised on the
      target no skill test has touched.

## 3. Record it

- [x] 3.1 Add the location rule to the Publishing section of `docs/06-spec-sources.md`, where the
      publish rules for all targets are already stated once, and verify the wording matches what both
      publishers now return.
- [x] 3.2 Strike item 4 from *Builder — skill target* in `docs/12-backlog.md` and verify the page no
      longer claims a skill over `git:` is untested.
- [x] 3.3 Run `make lint` and then the **whole reactor** — `make build` followed by `make test`, not
      just the two touched modules — and verify both are green with no endpoint configured. This is a
      condition of the change, not a courtesy: `PublishResult` is a cross-module type and this repo's
      studio test-compile has silently broken on a builder API change before.

---

Verification actually run (2026-11-18):

- 1.1–1.2: `make build` exit 0; `PublisherTest` green.
- 1.3: the `git:` report line is pinned offline by a new assertion in
      `gitPublishCommitsAndPushesIntoALocalRepo` (a live `git:` build needs an endpoint, which the
      default run excludes): it asserts `BuildResult.report()` contains
      `published:   git:<repo>#explainer (pushed)`. Studio test-compile and tests: 119 green.
- 2.1–2.2: `aSkillPublishedToGitLandsInACommitThatOutlivesThePublisher`. The publish pushes, because a
      default (no-push) publish commits only in the working tree and would leave the bare repository
      empty. The location is read after `close()`. The local bare repository is itself under a temp
      directory, so "does not resolve under a temporary directory" is asserted as: the location equals
      `git:<repo>#<unit>`, does not contain the `smith-git-publish-` working-tree marker, and does not
      resolve to an existing path.
- 2.3: `aSkillCollidesInAGitSourceAndOverwriteReplacesTheDirectory`.
- Mutation check: reverting `GitPublisher` to report `unitDir` turns both location assertions red
  (`gitPublishCommitsAndPushesIntoALocalRepo:105`,
  `aSkillPublishedToGitLandsInACommitThatOutlivesThePublisher:142`), so neither is vacuous.
- 3.3: `make lint` → 0 Checkstyle violations, BUILD SUCCESS. `make build` exit 0. `make test` exit 0:
  smith-common 39, specsource 29, skillevaluator 942, runtime 43, builder 184 (was 182; PublisherTest
  3 → 5 tests), studio 119. Note: `includeTestSourceDirectory` is `false` in the root pom, so `make
  lint` does not check the test sources this change mostly touched.
