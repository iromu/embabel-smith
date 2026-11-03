---
title: "Get started"
type: "overview"
status: "active"
language: "default"
source_paths:
  - "README.md"
  - "pom.xml"
  - "run-loop.sh"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
updated_at: "2026-11-03"
---

# Get started

A smart newcomer's first ten minutes. This is codebase orientation, not a duplicate of `README.md`
(which carries the per-POC command table for all twenty POCs).

## The one command that always works

```bash
mvn -q verify
```

Builds every module and runs every **offline** test. This needs no model endpoint and no network
beyond Maven's first-time dependency fetch (Embabel 1.5.3 comes from `repo.embabel.com`, declared
in the parent `pom.xml`). A green `mvn -q verify` is the project's baseline: it proves the format
round-trips, validation, the builder's validate/write steps, eval scoring, the skill-format
surface, the retrieval benchmark, the progressive-disclosure measurement, and the trust-tier
allowlist all behave.

## To actually *run* a POC (needs a model endpoint)

Endpoint-gated POCs call a live LLM. Point at any OpenAI-compatible server (LiteLLM / OpenAI /
Ollama):

```bash
export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=my-model
export EMBABEL_MODELS_DEFAULT_LLM=my-model
```

Then install the modules once so sibling artifacts resolve, and run a single module by `-pl`:

```bash
mvn install -DskipTests
BUILDER_PACK_DIR="$PWD/skill-packs/explainer" \
BUILDER_INPUT="Explain how a coffee machine works" BUILDER_PLANNER=GOAP \
    mvn -pl poc/poc-1-no-code-runtime spring-boot:run
```

Expected successful output: `=== Skill output ===` followed by the explainer text the model
produced. If you skipped the env vars, you get a **clear configuration error** naming the missing
variables — that is intended, not a bug. See [[model-endpoint]].

**Inputs are OS env vars, not `-D`.** `spring-boot:run` forks the app with the module directory as
its working directory and does **not** forward Maven `-D` properties into it, so `-Dbuilder.*`
silently falls back to defaults. Env vars *are* inherited by the forked JVM, and Spring's relaxed
binding maps `builder.X` → `BUILDER_X`. Use an **absolute** `BUILDER_PACK_DIR` (the relative default
does not resolve from the module's working directory).

## The full build → run loop

`run-loop.sh` chains the two endpoint-gated steps into one command: it builds a skill with the
builder, captures the written pack path, and runs that pack in the no-code runtime.

```bash
./run-loop.sh "A skill that explains a topic in plain language and posts it as a background message" \
              "Explain how a coffee machine works"
```

It fails fast with a clear message if the endpoint env vars are unset, and surfaces the builder's
output if the built draft fails validation (design D5 — an invalid skill is never written).

## First files to read (in order)

1. `README.md` — module table + per-POC run commands.
2. `docs/skill-pack-format.md` — the on-disk contract. See [[skill-pack-format]].
3. `skill-packs/explainer/` — the concrete sample pack (manifest, goal, two steps, one type).
4. `poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/SkillRunner.java` — the
   keystone run path. See [[runtime-run]].

## One safe first change

Edit the prompt in `skill-packs/explainer/steps/writeExplainer.yml` (e.g. change the word count)
and re-run POC 1. You change **data only** — no code, no build, and the validator still passes
because the prompt references (`{{topicBrief.topic}}` etc.) are unchanged.

## One tempting dangerous change

Removing the guard in `poc/poc-3-builder-agent/.../BuilderAgent.java` where `writeSkillPack` throws
on an invalid `ValidatedDraft`. That guard is the whole safety case for "generate with no code" —
see [[validate-before-write]]. Don't remove it to make a test pass.
