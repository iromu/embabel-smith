---
title: "Model endpoint and endpoint-gating"
type: "concept"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/config/ModelEndpointConfig.java"
  - "docs/05-architecture.md"
  - "prod/embabel-smith-studio/README.md"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/rest/StatusController.java"
  - "prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/config/StudioConfiguration.java"
  - "docs/04-poc-run-results.md"
  - "docs/08-builder.md"
  - "poc/poc-1-no-code-runtime/src/main/resources/application.yml"
  - "poc/poc-1-no-code-runtime/src/main/java/com/embabel/smith/runtime/NoCodeRuntime.java"
updated_at: "2026-11-26"
---

# Model endpoint and endpoint-gating

No model endpoint is hardcoded anywhere. A run is pointed at a model at run time through environment
variables, and which backend it talks to is **detected** from the environment rather than configured.
The settled wording of the detection order lives in `docs/05-architecture.md` §The model layer; this page
records what that means for code edited here.

## Detection order

`ModelEndpointConfig.detect(...)` (in `smith-common`) decides the provider once, from the six values a
run resolves: the three `OPENAI_CUSTOM_*` variables, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` and the default
LLM. First match wins — any one of the three `OPENAI_CUSTOM_*` set selects the OpenAI-compatible endpoint,
else `OPENAI_API_KEY` selects the bundled OpenAI catalog, else `ANTHROPIC_API_KEY` selects Anthropic's,
else the provider is `NONE` and a model-backed call is refused before it reaches a client. `provider()`
reports which won, so a machine holding more than one key uses the first of them.
`EMBABEL_MODELS_DEFAULT_LLM` names the model whichever way the provider was chosen.

```bash
export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
export OPENAI_CUSTOM_API_KEY=sk-...
export OPENAI_CUSTOM_MODELS=my-model
export EMBABEL_MODELS_DEFAULT_LLM=my-model
```

The custom form feeds Embabel's `openai.custom` config, declared in each POC's `application.yml` as
`${OPENAI_CUSTOM_BASE_URL:}` etc. (the `:` means "empty default" — see
`poc/poc-1-no-code-runtime/src/main/resources/application.yml`). The two catalog providers need no model
list, since Embabel registers its own bundled catalog; their base URL comes from Embabel's defaults and
is overridable by `OPENAI_BASE_URL` / `ANTHROPIC_BASE_URL` (the full env-and-property table is
`prod/embabel-smith-studio/README.md`). The POC launchers read the same bridge but are OpenAI-compatible
only.

## The "clear configuration error" contract

`isConfigured()` is `configError() == null`, and what it demands is **provider-relative**: each
`Provider` carries its own `requiredEnvVars()`, so `openai-custom` needs `baseUrl` + `apiKey` + `models`
while `openai` and `anthropic` need their single key plus `EMBABEL_MODELS_DEFAULT_LLM`. When nothing at
all is set the message names all three routes rather than guessing one; otherwise it names only the
variables the winning provider still lacks — a **clear message**, not an opaque model error.

Every POC launcher checks it in `run()` before doing anything else, so starting an endpoint-gated POC
without the variables fails fast (`poc/poc-1-no-code-runtime/.../NoCodeRuntime.java`, covered by
`ModelEndpointConfigTest`).

## In `prod/`: the gate only, never the model name

`prod/` classes hold `ModelEndpointConfig` for exactly two reasons: asking **whether** a model-backed
call can run (`isConfigured()` / `configError()`, which is how the REST endpoints produce the structured
`MODEL_NOT_CONFIGURED` response) and, in `rest/StatusController.java`, reporting the configured default
LLM for the UI's readiness chip. Nothing in `prod/` picks a model to call — the platform's configuration
selects it, which is what makes per-role model choices possible at all. The deleted raw callers used to
do `models.split(",")[0]` themselves. See [[model-calls-go-through-ai]].

Two places do look at *which* provider won: `config/StudioConfiguration.java`, which builds the bean
with `detect(...)`, and `rest/ModelsController.java`, which offers the endpoint's own model list for
`OPENAI_CUSTOM` but Embabel's resolved list for the catalog providers. Each `prod/` module's
`application.yml` binds the key variables to a placeholder default (`sk-smith-no-endpoint`; the studio's
is `sk-studio-no-endpoint`) — Embabel's provider starters refuse to boot without a key at all, so the
placeholder lets a module start and fail at the gate rather than at context startup.

The POC launchers differ: several build their own OpenAI-compatible HTTP caller, because that raw arm is
what POC 24 and POC 25 are measuring. Deliberate — the guard tests are `prod/`-only.

## The measured ceiling

Whether an endpoint exists is configuration; what it can service is measured, and the numbers are on the
record in `docs/08-builder.md`. Builder live runs die on Embabel's 60-second interaction ceiling, not on
a builder setting: the recorded runs end on `LLM call for interaction action:surveyBlueprint timed out
after 60,000ms`, and the two runs given a ninety-minute budget each stopped itself at about 53 minutes —
inside its own cap, so `docs/08-builder.md` states that **no value of `SMITH_PARITY_TIMEOUT` is the
binding constraint** (that variable is the parity script's own wall-clock cap, 2700 s by default in
`examples/parity-agentic.sh`). Throughput behind it is measured, not assumed: roughly **2.96 tokens/s**
on a contended probe, against 8.5–30.7 tokens/s at quieter hours — so a spec-sized answer needs several
times the 60 s the platform allows, and ten retries do not change the arithmetic. Throughput moves; the
60 s does not. The clock belongs to the interaction, not to its last call, so a step that spends part of
it on tool round-trips has less left for its own drafting call.

## Offline vs. endpoint-gated

This is the project's central split:

- **Offline** (always runs in CI via `mvn -q verify`): format round-trip, validation, the builder's
  validate/write steps, the eval scoring/composite, the spec-format surface, planner resolution, type
  mapping, step-spec assembly. Pure Java, testable without a model.
- **Endpoint-gated** (requires a live endpoint): anything that actually calls the LLM — the builder's
  `compileFromDescription`, the runtime's goal run, the NL-compile, the eval invocation, the full
  build→run loop.

In `prod/` the marker is the JUnit `live` tag — excluded by the build's default `test.excludedGroups`,
selected only by `make test-live` — and in the POCs it is the launcher's endpoint check. Which of them were
actually run is recorded in `docs/04-poc-run-results.md`. See [[what-is-embabel-smith]] and
[[getting-started]].
