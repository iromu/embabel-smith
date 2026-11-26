---
title: "Get started"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "README.md"
  - "pom.xml"
  - "Makefile"
  - "setup.sh"
  - "run-loop.sh"
  - "scripts/native.sh"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
  - "prod/embabel-smith-studio/README.md"
  - ".github/workflows/offline-verification.yml"
  - "docs/04-poc-run-results.md"
  - "docs/05-architecture.md"
updated_at: "2026-11-26"
---

# Get started

A smart newcomer's first ten minutes. `README.md` is an overview — two diagrams, the sample pack, and
pointers to `docs/index.md` and `.wiki/index.md` — and holds no command reference, so the run book this
page summarizes lives in `docs/`: the per-POC launchers in `docs/04-poc-run-results.md` §8, the build
and test entry points and the live-group opt-in in `docs/05-architecture.md`.

## The one command that always works

```bash
mvn -q verify
```

Builds every module in the **default reactor** and runs every **offline** test — that is
`smith-common` and `prod/`; the POCs sit behind a profile, so add `POC=1` / `-Ppoc` to build them.
This needs no model endpoint and no network beyond Maven's first-time dependency fetch (Embabel comes
from `repo.embabel.com`, declared in the parent `pom.xml`). A green `mvn -q verify` is the local
baseline: it proves the format round-trips, validation, the builder's validate/write steps, eval
scoring, the spec-format surface, the retrieval benchmark, the progressive-disclosure measurement, and
the trust-tier allowlist all behave.

The root `Makefile` exists so nobody invents their own `mvn` invocation — see [[build-tooling]]. Its
`build` target is `compile -DskipTests`, so it is **not** a substitute for `verify`; the targets worth
knowing are `test`, `test-module`, `lint`, and `module`. **CI runs those targets, not `verify`** —
`.github/workflows/offline-verification.yml` runs `make build`, `make test` and `make lint`, which
differ from the bare command in that they interpolate `$(POC_FLAG)` and, for `test`, source
`~/.config/env/llm.env` first. One more target is worth knowing before you go looking for it:
`make native` builds the builder's native `smith` binary into
`prod/embabel-smith-builder/target/smith` (`Makefile:72-73`), and needs a GraalVM JDK with
`native-image` on `PATH` — `scripts/native.sh` is the sdkman variant, which prefers the **25.0.2** patch
CI pins and warns when it falls back to a rolling candidate. The same build puts a `specs/` folder beside
the binary, at `prod/embabel-smith-builder/target/specs`: an image carries its resources as a flat named
set with no directory entries, so a pack flow reads that folder from disk rather than from inside the
image, and a `smith` with nothing beside it refuses on its first pack flow. What that
binary is for is on [[native-binary]]. The README names no target but `make build`, `make test` and
`make help`, so `native` is a fact about the `Makefile`, not about the README.

**The endpoint-gated tests are off by default, and `make test` does not change that.** The live group is
excluded through the overridable `test.excludedGroups` property (default `live` in the root POM), so
`make test` stays green with no endpoint configured — it sources `~/.config/env/llm.env` and exports the
model variables, but the tag exclusion still applies. **`make test-live` is the only supported opt-in**
(it clears the exclusion *and* selects the group; `mvn test -Dgroups=live` alone selects nothing, because
the exclusion is still in force). An IDE launch that did not inherit your shell environment will skip
them silently instead ([[model-endpoint]]). For one module:
`make test-module MODULE=prod/embabel-smith-studio` — that target passes `$(POC_FLAG)`, and a `poc/...`
`MODULE` value switches the profile on by itself (`Makefile:12`), so
`make test-module MODULE=poc/poc-1-no-code-runtime` needs no `POC=1`.

Before your first commit, run `make setup` once — it installs the pre-commit hooks (gitleaks,
whitespace, and `make lint`), which are otherwise **off** for a fresh clone ([[build-tooling]]).

## To actually *run* a POC (needs a model endpoint)

Endpoint-gated POCs call a live LLM. Point at any OpenAI-compatible server (LiteLLM / OpenAI /
Ollama):

```bash
export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=my-model
export EMBABEL_MODELS_DEFAULT_LLM=my-model
```

Which provider a run resolves to is decided once, in a fixed first-match order: the
`OPENAI_CUSTOM_*` trio selects the OpenAI-compatible endpoint, else `OPENAI_API_KEY` selects the bundled
OpenAI catalog, else `ANTHROPIC_API_KEY` selects the bundled Anthropic catalog, else the provider is
`NONE` and every model-backed call is refused before it reaches a client
(`docs/05-architecture.md`). The POC launchers read the same bridge but are OpenAI-compatible only; the
full env-and-property table is in `prod/embabel-smith-studio/README.md`.

Then install the modules once so sibling artifacts resolve, and run a single module by `-pl`. The
`-Ppoc` profile is **required** — the POCs are not in the default reactor, so `-pl poc/...` matches
nothing without it:

```bash
mvn install -DskipTests
BUILDER_PACK_DIR="$PWD/agent-specs/explainer" \
BUILDER_INPUT="Explain how a coffee machine works" BUILDER_PLANNER=GOAP \
    mvn -Ppoc -pl poc/poc-1-no-code-runtime spring-boot:run
```

Expected successful output: `=== Spec output ===` followed by the explainer text the model
produced. If you skipped the env vars, you get a **clear configuration error** naming the missing
variables — that is intended, not a bug. See [[model-endpoint]].

**Inputs are OS env vars, not `-D`.** `spring-boot:run` forks the app with the module directory as
its working directory and does **not** forward Maven `-D` properties into it, so `-Dbuilder.*`
silently falls back to defaults. Env vars *are* inherited by the forked JVM, and Spring's relaxed
binding maps `builder.X` → `BUILDER_X`. Use an **absolute** `BUILDER_PACK_DIR` (the relative default
does not resolve from the module's working directory).

## The full build → run loop

`run-loop.sh` chains the two endpoint-gated steps into one command: it builds a spec with the
builder, captures the written pack path, and runs that pack in the no-code runtime.

```bash
./run-loop.sh "An agent that explains a topic in plain language and posts it as a background message" \
              "Explain how a coffee machine works"
```

It fails fast with a clear message if the endpoint env vars are unset, and surfaces the builder's
output if the built draft fails validation (design D5 — an invalid spec is never written).

## First files to read (in order)

1. `README.md` — the short overview: what the project does and one command to run it. The per-POC run
   commands are in `docs/04-poc-run-results.md` §8, the build and test entry points in
   `docs/05-architecture.md`.
2. `docs/agent-spec-format.md` — the on-disk contract. See [[agent-spec-format]].
3. `agent-specs/explainer/` — the concrete sample pack (manifest, goal, two steps, one type).
4. `poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SpecRunner.java` — the
   keystone run path. See [[runtime-run]].

## One safe first change

Edit the prompt in `agent-specs/explainer/steps/writeExplainer.yml` (e.g. change the word count)
and re-run POC 1. You change **data only** — no code, no build, and the validator still passes
because the prompt references (`{{topicBrief.topic}}` etc.) are unchanged.

## One tempting dangerous change

Removing the guard in `poc/poc-3-builder-agent/.../BuilderAgent.java` where `writeAgentSpec` throws
on an invalid `ValidatedDraft`. That guard is the whole safety case for "generate with no code" —
see [[validate-before-write]]. Don't remove it to make a test pass.
