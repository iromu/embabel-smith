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
updated_at: "2026-11-15"
---

# Build and contribution tooling

The Maven commands in [[getting-started]] still work. The root `Makefile` exists so that neither you
nor an AI agent invents its own `mvn` invocation — its header says exactly that. Prefer the targets.

## The Makefile

`MVN` prefers the wrapper `./mvnw` and falls back to system Maven (`Makefile:6`), so everyone builds
with the same Maven version.

| Target | What it runs |
| --- | --- |
| `build` | `mvn -q compile -DskipTests` |
| `test` | `mvn test`, **after sourcing the model-endpoint env** |
| `test-module` | `mvn test -pl $(MODULE) -am` |
| `lint` / `checkstyle` | `mvn checkstyle:check` (both are aliases) |
| `checkstyle-module` | `mvn checkstyle:check -pl $(MODULE)` |
| `module` | `mvn $(GOAL) -pl $(MODULE) -am -DskipTests` |
| `install` / `clean` | `mvn install -DskipTests` / `mvn clean` |
| `setup` | `./setup.sh` |

`make help` lists them from the `## ` comments.

**The `test` target is the reason to use the Makefile.** It sources `$HOME/.config/env/llm.env` and
exports `OPENAI_CUSTOM_BASE_URL`, `_API_KEY`, `_MODELS`, and `EMBABEL_MODELS_DEFAULT_LLM` before
running Maven ([[model-endpoint]]). That is the difference between the endpoint-gated tests actually
running and silently skipping — which is exactly what happens when you launch from an IDE that did not
inherit the shell environment. `make test` and `make test-module MODULE=poc/poc-1-no-code-runtime` are
the reliable paths.

## Pre-commit hooks are opt-in per clone

`make setup` runs `setup.sh`, which: installs `pre-commit` (pipx, else `pip3 install --user`, else it
fails rather than half-installing), runs `pre-commit install`, installs the project skills from
`skills-lock.json` via `npx skills update`, and installs the OpenSpec CLI if `openspec` is missing.

`.pre-commit-config.yaml` runs four things: **gitleaks** (secret scanning), `end-of-file-fixer`,
`trailing-whitespace`, and a local hook `make-lint` that shells out to `make lint`.

Note `core.hooksPath` is **unset** — the hooks live in `.git/hooks`, installed per clone. A fresh clone
that never ran `make setup` commits with no checks. (`setup.sh` handles one specific conflict: if the
install fails because `core.hooksPath` is set, it unsets it and retries.)

## The style gate is not optional

The checkstyle plugin is bound to Maven's **`validate` phase** and `<inherited>true</inherited>`, so
**every module build runs it** — a style violation fails the build before compilation
(`pom.xml:343-374`). Two details that trip people up:

- `includeTestSourceDirectory` is **false** — tests are not style-checked, main sources are.
- The plugin is configured at plugin level, not just in an execution, specifically so the bare
  `mvn checkstyle:check` CLI goal uses the project config instead of the plugin's `sun_checks.xml`
  default.

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
