# Design

## Context

Facts this design has to work with, each checked in this tree:

- `.github/workflows/` contains only `wiki-pages.yml`. It triggers on pushes to `poc/builder` that
  touch `.wiki/**` or itself, plus `workflow_dispatch`, and its jobs build and deploy a static site.
  It never runs Maven.
- The repository's default branch is `poc/builder` (`refs/remotes/origin/HEAD`), while work happens on
  topic branches such as `poc/iteration-2`. A workflow that triggers only on the default branch leaves
  every topic branch unchecked, which is where the regressions actually live.
- The `Makefile` is the documented entry point: `build` (compile, skip tests), `test` (all tests, live
  group excluded), `lint`/`checkstyle` (SpringChecks). `MVN` resolves to `./mvnw` when the wrapper is
  present, so CI uses the pinned wrapper without being told to.
- `make test` begins `[ -f "$HOME/.config/env/llm.env" ] && . … && export …; <mvn>`, so the entry point
  is not itself offline: on a development machine that file exists and the test JVM really is handed a
  working endpoint (a local `make test` logs `OpenAiModelsConfig - Using custom OpenAI base URL:
  http://spark.local:4000/v1`). What keeps the default suite model-free is already settled project
  wording, not a decision this change makes — `README.md`, §"Live tests are opt-in by tag": "So a plain
  `mvn test` runs no live test whether or not an endpoint happens to be configured — and `make test`
  still exports the endpoint env, it just no longer decides whether live tests run." Two facts, both
  true and both needed: on a GitHub runner the file is absent, the guard short-circuits, and the Maven
  line runs with nothing exported; and the live group is excluded by the pom's default
  `test.excludedGroups=live` regardless of what is exported. The workflow needs no change to the target
  and is not what keeps the run model-free.
- Live tests carry `@Tag("live")`, which is what the excluded group selects. Opting in requires the
  flag pair `-Dtest.excludedGroups= -Dgroups=live`, which the workflow must not do.
- `pom.xml` sets `java.version` and `maven.compiler.release` to **21**; the POC reactor sits behind the
  `poc` Maven profile, off by default.

## Goals / Non-Goals

**Goals:**

- A push or a pull request fails when the reactor does not compile, when the offline suite fails, or
  when SpringChecks are violated — without anyone remembering to run anything.
- The run is honest about its scope: it proves the offline guarantees and claims nothing about the
  model-driven paths.

**Non-Goals:**

- Running the live, endpoint-driven tests, on any schedule. That spends money per run and needs
  endpoint secrets in GitHub.
- Building the POC reactor. `poc/` is frozen evidence.
- Coverage thresholds, publishing, or deploying anything.

## Decisions

**D1 — Three commands, no new script.** The workflow calls `make build`, `make test` and `make lint`
rather than invoking Maven directly or wrapping them in a script. The `Makefile` is already the
project's contract for "how an agent runs a build", it resolves the wrapper, and a failure in CI then
reproduces with the same command the developer runs. Rejected: a `ci.sh` — a second place for the
command list to drift out of date.

**D2 — No endpoint, and no attempt to provide one.** The workflow sets no model environment and no
secrets. This is a positive requirement, not an absence: a CI that quietly gains an endpoint becomes a
CI whose green depends on a model's mood, and this repo already has three tests that fail on live
model output rather than on code. Rejected: an endpoint-protected job for the four gated tests, which
converts a deterministic gate into a flaky one and puts spend on every commit.
*The no-endpoint rule here is a policy choice; the mechanism that keeps the suite model-free is the
pom's, and `README.md` (§"Live tests are opt-in by tag") already states it: "a plain `mvn test` runs no
live test whether or not an endpoint happens to be configured — and `make test` still exports the
endpoint env, it just no longer decides whether live tests run."* Two facts hold on a runner and both
should be said: nothing is exported there because the env file is absent, and the live group is excluded
by `test.excludedGroups=live` regardless of what is exported. Only the second is load-bearing — `make
test` on a machine that has `~/.config/env/llm.env` does configure a live endpoint inside the test JVM,
and the reason no model is called is that the exclusion removes the tests that would call it. So the
workflow's "no secrets" rule removes the *credential* path, and the group exclusion is what a reviewer
must check if the run ever starts looking model-dependent: adding `-Dtest.excludedGroups=` to the
workflow without adding credentials would go red on the endpoint-gated tests rather than silently spend
money.

**D3 — Triggers on pull requests and on pushes to the default branch.** `pull_request` is where the
value is, because the regressions in the record were introduced on topic branches. Rejected: push-only
on the default branch, matching `wiki-pages.yml` — that would verify a regression only after it had
merged. Rejected: push on every branch, which doubles spend for branches that may never be proposed.

**D4 — JDK 21, from the pom, not from the runner default.** The runner image's default JDK drifts; the
build targets release 21. The workflow pins it explicitly and a task verifies the pin matches the pom,
so a future `java.version` bump shows up as a failing check rather than a local-only build.

**D5 — The default reactor only, and that is stated in the workflow's own comment.** Adding `POC=1`
would re-verify frozen evidence on every commit and lengthen the run for no gate. The omission is
recorded where a reader will hit it, because "CI is green" and "the POCs still run" are different
claims.

## Risks / Trade-offs

- **Green CI will be read as "verified".** Mitigated by D2's honesty requirement and by the
  `docs/12-backlog.md` edit, which must keep naming the four endpoint-gated paths as unproven.
- **The offline suite is the gate, so a slow suite slows the project.** The offline reactor suite ran
  in well under a minute locally; if it grows, the fix is parallelism, not a skipped gate.
- **`make lint` failing the build on style may block an urgent fix.** Accepted: `make lint` is already
  a pre-commit hook, so CI enforces what local hooks already require.
- **Nothing here verifies the wiki, the studio frontend build, or Docker-backed POCs.** Out of scope by
  design; the frontend is built as part of the reactor, so a broken SPA build does fail the run.
