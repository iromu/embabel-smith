---
title: "Build and contribution tooling: Makefile, hooks, and the style gate"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "Makefile"
  - "setup.sh"
  - ".pre-commit-config.yaml"
  - "pom.xml"
  - "checkstyle.xml"
  - "checkstyle-header.txt"
  - "checkstyle-suppressions.xml"
  - "docker/docker-compose-infra.yml"
  - ".github/workflows/offline-verification.yml"
  - ".github/workflows/native-image.yml"
  - "scripts/native.sh"
  - "examples/parity-agentic.sh"
  - "docs/08-builder.md"
  - "docs/12-backlog.md"
updated_at: "2026-11-26"
---

# Build and contribution tooling

The Maven commands in [[getting-started]] still work. The root `Makefile` exists so that neither you
nor an AI agent invents its own `mvn` invocation — its header says exactly that. Prefer the targets.

## The Makefile

`MVN` prefers the wrapper `./mvnw` and falls back to system Maven (`Makefile:6`), so everyone builds
with the same Maven version.

| Target | What it runs |
| --- | --- |
| `build` | `$(MVN) $(POC_FLAG) -q compile -DskipTests` |
| `test` | `$(MVN) $(POC_FLAG) test`, **after sourcing the model-endpoint env** |
| `test-live` | `$(MVN) $(POC_FLAG) test -Dtest.excludedGroups= -Dgroups=live` — the only opt-in to the `@Tag("live")` group; both flags are needed |
| `test-module` | `$(MVN) $(POC_FLAG) test -pl $(MODULE) -am` |
| `lint` / `checkstyle` | `$(MVN) $(POC_FLAG) checkstyle:check` (both are aliases) |
| `checkstyle-module` | `$(MVN) $(POC_FLAG) checkstyle:check -pl $(MODULE)` |
| `module` | `$(MVN) $(POC_FLAG) $(GOAL) -pl $(MODULE) -am -DskipTests` |
| `run-a2a` | installs the starter (`$(MVN) -q install -DskipTests -Dskip.npm=true -pl prod/embabel-smith-a2a-starter -am`), then `SPRING_PROFILES_ACTIVE=a2a SMITH_A2A_ENABLED=true SERVER_PORT=$(if $(SERVER_PORT),$(SERVER_PORT),18090) $(MVN) -q -pl prod/embabel-smith-studio spring-boot:run` — the studio serves the A2A surface; **the only target that opens a port** (18090 by default, unauthenticated; see [[a2a-endpoint]]) |
| `native` | `$(MVN) package -Pnative -DskipTests -pl :embabel-smith-builder -am` — the builder's `smith` binary; needs a GraalVM JDK with `native-image` on `PATH` ([[native-binary]]) |
| `install` / `clean` | `$(MVN) $(POC_FLAG) install -DskipTests` / `$(MVN) $(POC_FLAG) clean` |
| `setup` | `./setup.sh` |

`run-a2a` and `native` are the two targets that leave `$(POC_FLAG)` off: both name one module outright
rather than a slice of the reactor, so there is nothing for the profile flag to add.

`$(POC_FLAG)` is what makes the POC reactor reachable from a target instead of by accident: it expands
to `-Ppoc` when you set `POC=1`, and also when `MODULE` names a `poc/...` path, so
`make test-module MODULE=poc/poc-1-no-code-runtime` needs no extra flag (`Makefile:12`). With neither,
the POCs are simply not in the run.

`make help` lists them from the `## ` comments.

**The `test` target is the reason to use the Makefile.** It sources `$HOME/.config/env/llm.env` and
exports the model variables — `OPENAI_CUSTOM_BASE_URL`, `_API_KEY`, `_MODELS`, `OPENAI_API_KEY`,
`ANTHROPIC_API_KEY` and `EMBABEL_MODELS_DEFAULT_LLM` — before running Maven (`Makefile:23`, repeated
verbatim in `test-live` and `test-module`) ([[model-endpoint]]). That is the difference between the endpoint-gated tests actually
running and silently skipping — which is exactly what happens when you launch from an IDE that did not
inherit the shell environment. `make test` and `make test-module MODULE=poc/poc-1-no-code-runtime` are
the reliable paths.

## CI calls the Makefile, not Maven

`.github/workflows/offline-verification.yml` fires on a push to `main` and on every pull request. Its
first step is a pin check rather than a build: it extracts `<java.version>` from `pom.xml` and fails
unless it equals the workflow's `JAVA_VERSION` (`21`, matching `pom.xml:25`), so a Java bump that
forgot the workflow fails visibly instead of building against a different JDK. Then
`actions/setup-java` (temurin, with the Maven cache) and three steps — `make build`, `make test`,
`make lint`. Nothing else: the workflow deliberately calls the Makefile so a red run reproduces with
the command a developer runs.

The workflow header states what a green run does **not** cover, and the claims are checkable: the
**default reactor only** (`poc/` sits behind the `poc` profile and is not built); **no model endpoint
and no secrets** — on a runner the env file `make test` sources does not exist, so nothing is
exported, and the live group stays excluded through the root POM's `test.excludedGroups` default
regardless; the `@Tag("live")` tests therefore do not run, and green says nothing about the paths only
they exercise; **nothing is published or deployed**; and **no coverage threshold** is enforced. See
[[getting-started]] for the offline/endpoint-gated split this encodes. One cross-reference inside that
header is stale: it still points at `README.md`, "Live tests are opt-in by tag" — a section the README
rewrite removed on 2026-11-26. What it points at is unchanged and stated in
[`docs/05-architecture.md`](../../docs/05-architecture.md).

A second workflow, `.github/workflows/native-image.yml`, compiles the builder into a native binary on
every PR that could plausibly change it and smoke-tests the result — again through `make native`, never
a hand-written `mvn` line, so a red run reproduces with a command a developer can type. It is triggered
by pull request and manual dispatch only (no push trigger), and unlike the offline workflow it is
triggered by *paths*: a PR touching no Java, no `pom.xml`, no resources and no `Makefile` does not pay
for a native compile. What its green does and does not prove is on [[native-binary]].

## The parity harness is the one instrument that is not CI

`examples/parity-agentic.sh` builds the same blueprint inputs under different `--flow` arms and prints
the columns a verdict is read from. Nothing invokes it automatically: `git grep -n parity-agentic HEAD
-- Makefile .github` returns nothing, so it is neither a CI step nor a `make` target. The arm is
`SMITH_PARITY_FLOW` — `agentic` by default, `harness` the alternative, anything else a usage error that
exits 2 before anything is resolved or spent (`examples/parity-agentic.sh:106-126`). Its header gives
the reason it is a script rather than a test: it spends model calls on purpose, which is a decision a
human makes about whether an answer is worth paying for. What the arms concluded is on [[smith-builder]].

**The columns need reading before they are believed**, and the script's own header carries that
reasoning because two of the four were misread once (`examples/parity-agentic.sh:45-62,205-216`):

- `answered` = `grep -c 'received LLM response'`. The platform prints that line for an **action**'s
  response, not for each round inside a tool loop — those go out as `starting tool loop [...]` lines —
  so on a tool-loop arm the column counts landed action-level interactions and under-counts everything
  the loop did. `answered=0` beside hundreds of logged tool calls does **not** mean the model never
  answered: every one of those calls is a completion this grep never sees (`docs/08-builder.md:638-639,652`
  records 386 and 440 calls against runs whose column read 0).
- `spent` = `grep -cE 'Retry attempt|timed out after'` is a **line** count, not an event count — about
  2.3× the events. Counted on one recorded log, its 116 matches decompose into 50
  `ChatClientLlmOperations … attempt N timed out` lines, 50 `LlmDataBindingProperties … Retry attempt N
  of 10 due to: … timed out` lines carrying the same timestamps as those 50 timeouts, and 16
  exception/summary lines. One timeout logs two lines.
- `offered` = `grep -cE 'starting tool loop \[[^]]'` counts **loops**, not calls, and the bracketed list
  is that step's own tool surface, which differs by input — so `offered>0` says a step was offered
  something, never that it declined a validator.
- `called` only became trustworthy at commit `19df658`. The pattern was `calling tool [A-Za-z]+\(`,
  which cannot match a snake_case name, and every tool a build actually reaches is snake_case
  (`read_doc`, `list_docs`, `list_packs`, `rank_packs`, `assemble_corpus`, `scan_spec`). It printed 0
  against logs holding hundreds of calls, so a zero in that column in any earlier run said nothing
  about whether the step used its tools.

**Evidence hygiene in `examples/out/`.** Each input writes `build.log`, `report.md` and `exit.txt` into
`examples/out/<blueprint>/<flow>/` (`examples/parity-agentic.sh:63,171,193-197`). Separate arms get
separate folders, so an agentic run and a harness run do not clobber each other — but a second run of
the *same* arm does, in place and at different moments: `build.log` truncates and fills continuously
while `report.md` and `exit.txt` are written only when the build ends. Two files in one folder can
therefore come from two different builds. The repo tracks none of it: `examples/out/` is gitignored
(`.gitignore:20`) and `git ls-tree -r --name-only HEAD -- examples/out` returns 27 files, all published
YAML under `examples/out/TradingAgents/agent-spec-single-shot/published/` — no log, report or exit file
for any arm. So a number quoted in a document came from somebody's local tree, not from the repository,
and the local tree shows how a cited run gets frozen: `examples/out/evidence-2026-11-23/` carries a
README recording that it was copied `cp -a` from every `<input>/<flow>/` directory "before the 17:06
parity driver overwrote the agentic and harness ones".

**The binding constraint on these runs is endpoint throughput, not the timeout knob**, and it is
measured rather than assumed: a 16-token completion took 16.7 s with `finish_reason=stop`, and a
600-token cap returned 294 tokens in 99.4 s ≈ 2.96 tok/s — against 8.5–30.7 tok/s at other hours with
nothing else running (`docs/08-builder.md:658-663`). A spec-sized generation needs several times the
60 s Embabel allows one interaction, and ten retries do not change that. The clock also covers the
tool-loop round-trips, which is why a tool-heavy step spends part of its own timeout: in one harness run
`planWhatToReadNext` landed in 59.332 s — 0.668 s inside the ceiling — after which
`draftAgentSpecFromRoots` spent 40 timeouts across 43 tool loops (`docs/08-builder.md:236-241`).

## Pre-commit hooks are opt-in per clone

`make setup` runs `setup.sh`, which: installs `pre-commit` (pipx, else `pip3 install --user`, else it
fails rather than half-installing), runs `pre-commit install`, installs the project skills from
`skills-lock.json` via `npx skills update`, installs the OpenSpec CLI if it is missing, and
finally curls three rule files — `typescript-best-practices`, `python-conventions`,
`jinja-llm-prompts` — out of `iromu/awesome-qwen` into `.qwen/rules/` (`setup.sh:55-63`). That last
step is a plain `curl -fsSL` under `set -euo pipefail`: with no network it is the failure that ends an
otherwise completed run. The OpenSpec CLI install outlived the planning trees: they were folded into
`docs/` and purged a second time on 2026-11-25, and `make setup` still installs the CLI so a new change
remains valid with `docs/` as what it edits ([[product-phase]]).

`.pre-commit-config.yaml` runs four things: **gitleaks** (secret scanning), `end-of-file-fixer`,
`trailing-whitespace`, and a local hook `make-lint` that shells out to `make lint`.

Note `core.hooksPath` is **unset** — the hooks live in `.git/hooks`, installed per clone. A fresh clone
that never ran `make setup` commits with no checks. (`setup.sh` handles one specific conflict: if the
install fails because `core.hooksPath` is set, it unsets it and retries.)

## The style gate is not optional

The checkstyle plugin is bound to Maven's **`validate` phase** and `<inherited>true</inherited>`, so
**every module build runs it** — a style violation fails the build before compilation
(`pom.xml:380-412`). Two details that trip people up:

- `includeTestSourceDirectory` is **false** — tests are not style-checked, main sources are.
- The plugin is configured at plugin level, not just in an execution, specifically so the bare
  `mvn checkstyle:check` CLI goal uses the project config instead of the plugin's `sun_checks.xml`
  default.

A sibling binding in the same block is easy to miss: `spring-javaformat-maven-plugin` is bound to
**`process-sources`** with the **`apply`** goal (`pom.xml:367-379`). Any invocation that walks the
lifecycle — `make build`, `make test`, `make install` — therefore **rewrites source formatting in
place** before compiling, without reporting that it did. The check alone does not: `make lint` is a
direct `checkstyle:check` goal, which runs no lifecycle phase and so only reports. A file that passes
`make lint` can still come back from `make build` with different whitespace.

`checkstyle.xml` delegates to Spring's `SpringChecks` (from `spring-javaformat-checkstyle`, Checkstyle
12.3.1) with `headerFile=checkstyle-header.txt` and a copyright pattern of `20\d\d`. So every main
source file must open with exactly the two lines `checkstyle-header.txt` declares — the SPDX form of
the license statement, credited to **Ivan Rodriguez** and identifying `Apache-2.0` — and carry
`@author Ivan Rodriguez`; see the team memory on author headers. Commit `7805939` replaced the
13-line Apache boilerplate with these two lines in every source file, so a new file copied from an
older snippet will fail the gate.
`checkstyle-suppressions.xml` waives `JavadocPackage`, since the repo carries no
`package-info.java` files.

Commit `a7a977a` is the commit that made this true everywhere, clearing roughly 4300 `SpringChecks`
violations: `this.` prefixes, complete `@param`/`@return` Javadoc, four-group import order,
parenthesized ternaries and lambda parameters, catch variables named `ex`, inner types moved to the
end of the outer type, unused imports removed. Expect any new file to be held to the same rules.

## Development infrastructure

`docker/docker-compose-infra.yml` is the only compose file: a `postgres:16` for the studio
([[smith-studio-storage]]) on host port **5433** with a named volume and a `pg_isready` healthcheck.
Its header comment still documents the old `smith`/`smith_studio` credentials while the `environment:`
block says `embabel_smith` — trust the block.
