# Tasks

## 1. The workflow

- [x] 1.1 Add `.github/workflows/offline-verification.yml` triggered on `push` to the default branch
      and on `pull_request`, with a job that checks out, sets up the JDK at the version `pom.xml`
      declares (`java.version` / `maven.compiler.release`, currently 21) with Maven coming from the
      repository's `./mvnw`, and runs `make build`, then `make test`, then `make lint` — and verify
      the file parses (`actionlint` if available, else `gh workflow lint` or a YAML parse) and names
      no `secret`, no model environment variable and no `-Dgroups=live`.
      *(Added. `actionlint` is not installed and this `gh` has no `workflow lint` subcommand
      (`unknown command "lint" for "gh workflow"`), so the file was parsed with PyYAML: it loads, the
      trigger keys are `[push, pull_request]`, and the job's run steps are exactly `make build`,
      `make test`, `make lint`. `grep -n 'secrets\.' .github/workflows/offline-verification.yml` exits
      1 — no secret is referenced; the only occurrence of the word is the comment asserting that none
      are used. `grep -in 'OPENAI\|API_KEY\|ANTHROPIC\|groups=live'` finds nothing either. Maven comes
      from the wrapper through the `Makefile`, which resolves `MVN ?= ./mvnw` when the wrapper is
      present; the workflow installs no Maven of its own.)*
- [x] 1.2 State the run's scope in a comment at the top of the workflow: the default reactor only, so
      `poc/` is not built, and no model endpoint, so the live group stays excluded by the root pom's
      default. Verify the comment matches the triggers and steps actually written.
      *(The header comment names the three targets, the two triggers, the default-reactor-only scope
      and the excluded live group with the four test classes named. Cross-checked against the parsed
      document above: the triggers and steps it describes are the ones in the file.)*
- [x] 1.3 Verify the JDK pin cannot drift silently: read the pinned version and the pom property in
      the same check, and confirm they are equal, so raising `java.version` without editing the
      workflow is a visible mismatch.
      *(Implemented as the job's first step: it reads `<java.version>` from `pom.xml`, prints both
      values, and fails if they differ or the pom value is empty. `JAVA_VERSION` is one workflow-level
      `env` consumed by both that step and `setup-java`, so the pin being compared and the pin being
      installed cannot diverge from each other. The same shell logic run against this tree prints
      `pom.xml java.version=21 workflow pin=21` and passes; changing either side makes the step exit
      1.)*

## 2. Prove the commands are green with no endpoint

- [x] 2.1 Run `make build`, `make test` and `make lint` in an environment where `~/.config/env/llm.env`
      is **not** sourced and no `OPENAI_CUSTOM_*` variable is exported, and record the real exit status
      of each. This is the environment CI runs in, because the file-existence guard short-circuits when
      the file is absent — and the live group is excluded by the pom default regardless of what is
      exported, so what this pass confirms is the exclusion holding with no endpoint present.
      *(Reproduced by running all three with `HOME` pointed at a directory that has no
      `.config/env/llm.env` — its `.m2` and `.npm` symlinked to the real ones so the run stays
      incremental — and with `OPENAI_CUSTOM_BASE_URL`, `OPENAI_CUSTOM_API_KEY`,
      `OPENAI_CUSTOM_MODELS`, `OPENAI_MODEL`, `OPENAI_BASE_URL`, `OPENAI_API_KEY`, `ANTHROPIC_*` and
      `EMBABEL_MODELS_*` unset. Real exit statuses: `make build EXIT=0`, `make test EXIT=0`,
      `make lint EXIT=0`; the reactor summary is SUCCESS for all eight modules, with suite totals
      smith-common 39, specsource 29, skillevaluator 942, runtime 56, builder 192, studio 127 — 1385
      tests, 0 failures. What this proves is that the suite is green with nothing exported and the live
      group excluded; it does not make `make test` endpoint-blind, because that was never the
      environment's doing — `README.md` (§"Live tests are opt-in by tag") already fixes the mechanism as
      `test.excludedGroups`. The `Makefile` was not touched: the guard short-circuits here exactly as it
      does on a runner, and that only decides what is exported.)*
- [x] 2.2 Confirm the live tests did not run in that pass: the four `@Tag("live")` classes are reported
      as not-run, and `grep -c 'Tests run' ` output shows no live class. Verify by name
      (`LiveBuildIntegrationTest`, `LiveRunIntegrationTest`, `LiveLoopIntegrationTest`,
      `RestIntegrationTest`).
      *(`grep -E 'Running .*(LiveBuildIntegrationTest|LiveRunIntegrationTest|LiveLoopIntegrationTest|RestIntegrationTest)'`
      over that run's log exits 1 — none of the four started. The per-module `Skipped: 0` totals show
      they were excluded rather than skipped: the group never entered the selection. The same log shows
      no endpoint reached the JVMs from the environment — the only `Using custom OpenAI base URL` lines
      are the tests' own loopback fakes (`http://127.0.0.1:9/v1`, `http://127.0.0.1:35797/v1`) and the
      machine's real endpoint, `spark.local`, appears nowhere in the output.)*
- [x] 2.3 *(Added at review, during implementation.)* Correct `design.md`, which credited the Makefile
      guard with keeping the run model-free.
      *(Done in three places — the Context bullet, D2, and the workflow's own scope comment. Each now
      says both things rather than either: on a runner `~/.config/env/llm.env` is absent so the guard
      short-circuits and nothing is exported, **and** the live group is excluded by the pom's
      `test.excludedGroups=live` regardless of what is exported, with only the second being
      load-bearing. The mechanism is cited rather than re-derived — `README.md`, §"Live tests are opt-in
      by tag": "So a plain `mvn test` runs no live test whether or not an endpoint happens to be
      configured — and `make test` still exports the endpoint env, it just no longer decides whether
      live tests run." — which is settled project wording, so this change aligns to it instead of
      restating a rule. What the correction still establishes is that the entry point is not itself
      offline: on a development machine the file exists and the JVM really is handed a working endpoint
      (a local `make test` logs `OpenAiModelsConfig - Using custom OpenAI base URL:
      http://spark.local:4000/v1`), so the suite is model-free because of the group exclusion. D2 keeps
      its "no endpoint, no secrets" requirement as a policy choice and adds what a reviewer must check:
      clearing `test.excludedGroups` without adding credentials turns the run red on the endpoint-gated
      tests rather than silently spending money. Checked against this machine: the file exists, and the
      run in 2.1 is the guard short-circuiting in action. The `Makefile` is unchanged — that export is
      what lets `make test-live` reach the live tests.)*

## 3. Say what green means

- [x] 3.1 Add one line to `README.md`'s run book naming the workflow as where `make build`, `make test`
      and `make lint` are enforced automatically, and verify the commands it names are the commands the
      workflow runs.
      *(Added under "Build & test (offline)". The three targets it names are the three `run:` steps in
      the workflow.)*
- [x] 3.2 Add the same fact to `docs/05-architecture.md` beside the existing build entry points, and
      verify the page names the workflow file that exists.
      *(Added at the end of §Build entry points, naming `.github/workflows/offline-verification.yml`,
      which exists. The paragraph also states the scope limit and attributes the run's freedom from a
      model to the pom's group exclusion rather than to the workflow.)*
- [x] 3.3 Update the *Test coverage* bullet in `docs/12-backlog.md` so it states the offline suite now
      runs on every push and pull request **and** keeps naming the four endpoint-gated paths as not
      covered by it. Verify the bullet does not read as though the publish, run and loop paths are now
      verified.
      *(The bullet now says what changed is *who runs the offline suite, not what it proves*, that a
      green CI badge is the same evidence as a green local `make test` and nothing more, and that the
      four paths — and `poc/` — stay outside it. The original list of the four unproven paths is
      untouched above the addition, and the Check commands still find it.)*
